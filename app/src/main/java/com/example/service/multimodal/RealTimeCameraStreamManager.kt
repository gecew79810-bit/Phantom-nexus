package com.example.service.multimodal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * RealTimeCameraStreamManager:
 * Manages CameraX lifecycle, continuous image analysis stream at 15-30 FPS,
 * frame luminance computation, and live frame extraction for AI multimodal vision.
 */
class RealTimeCameraStreamManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "CameraStreamManager"
    }

    private var cameraExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _latestFrame = MutableStateFlow<Bitmap?>(null)
    val latestFrame: StateFlow<Bitmap?> = _latestFrame.asStateFlow()

    private val _latestAnalysis = MutableStateFlow(FrameAnalysisResult())
    val latestAnalysis: StateFlow<FrameAnalysisResult> = _latestAnalysis.asStateFlow()

    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps.asStateFlow()

    private var frameCount = 0L
    private var lastFpsCalculationTime = System.currentTimeMillis()
    private var framesSinceLastCalculation = 0

    // Callback invoked when a frame is ready for Gemini multimodal vision
    var onFrameAnalyzed: ((Bitmap, FrameAnalysisResult) -> Unit)? = null

    /**
     * Start live camera pipeline bound to the provided lifecycle owner.
     */
    fun startCameraStream(
        lifecycleOwner: LifecycleOwner,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
        onSurfacePreviewReady: ((Preview) -> Unit)? = null
    ) {
        if (_isStreaming.value) {
            Log.d(TAG, "Camera stream already running.")
            return
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                val preview = Preview.Builder()
                    .build()

                onSurfacePreviewReady?.invoke(preview)

                // Configure real-time image analysis pipeline
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor!!) { imageProxy ->
                    processImageProxy(imageProxy)
                }

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                _isStreaming.value = true
                Log.i(TAG, "CameraX multimodal analysis pipeline initialized successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind CameraX stream pipeline", e)
                _isStreaming.value = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Converts YUV_420_888 ImageProxy to NV21 ByteArray and then Bitmap,
     * computing luminance and frame rate statistics in real time.
     */
    private fun processImageProxy(image: ImageProxy) {
        try {
            frameCount++
            framesSinceLastCalculation++

            val now = System.currentTimeMillis()
            val elapsed = now - lastFpsCalculationTime
            if (elapsed >= 1000) {
                _fps.value = (framesSinceLastCalculation * 1000f) / elapsed
                framesSinceLastCalculation = 0
                lastFpsCalculationTime = now
            }

            // Calculate luminance from Y plane (first plane)
            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            var totalLum = 0L
            // Sample pixels for fast performance
            val step = 16
            var sampleCount = 0
            for (i in data.indices step step) {
                totalLum += (data[i].toInt() and 0xFF)
                sampleCount++
            }
            val avgLuminance = if (sampleCount > 0) totalLum.toFloat() / sampleCount else 0f
            val isLowLight = avgLuminance < 40f

            val analysis = FrameAnalysisResult(
                timestamp = now,
                width = image.width,
                height = image.height,
                averageLuminance = avgLuminance,
                isLowLight = isLowLight,
                frameCount = frameCount,
                description = "Luminance: ${avgLuminance.toInt()}/255 | FPS: ${String.format("%.1f", _fps.value)}"
            )
            _latestAnalysis.value = analysis

            // Convert image to Bitmap every N frames or on demand
            if (frameCount % 4L == 0L || _latestFrame.value == null) {
                val bitmap = imageProxyToBitmap(image)
                if (bitmap != null) {
                    _latestFrame.value = bitmap
                    onFrameAnalyzed?.invoke(bitmap, analysis)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing camera image proxy", e)
        } finally {
            image.close()
        }
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()
        val data = ByteArray(remaining())
        get(data)
        return data
    }

    /**
     * Converts YUV_420_888 to Android Bitmap via NV21 byte array conversion.
     */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 75, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    /**
     * Stops the camera stream and cleans up executor resources.
     */
    fun stopCameraStream() {
        try {
            cameraProvider?.unbindAll()
            cameraExecutor?.shutdown()
            cameraExecutor = null
            _isStreaming.value = false
            Log.i(TAG, "Camera stream stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera stream", e)
        }
    }
}
