package com.pantham.nexus.vision.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.pantham.nexus.vision.model.VisionRequest
import com.pantham.nexus.vision.pipeline.NexusVisionPipeline
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class NexusCameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val pipeline: NexusVisionPipeline,
    private val scope: CoroutineScope,
    private val onResult:
        (com.pantham.nexus.vision.model.VisionAnalysisResult) -> Unit
) {

    private val cameraExecutor =
        Executors.newSingleThreadExecutor()

    private var analysisUseCase:
            ImageAnalysis? = null

    fun start(
        requestProvider:
            () -> VisionRequest
    ) {

        val future =
            ProcessCameraProvider
                .getInstance(
                    context
                )

        future.addListener({

            val provider =
                future.get()

            val preview =
                Preview.Builder()
                    .build()

            preview.setSurfaceProvider(
                previewView.surfaceProvider
            )

            val analysis =
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(
                        ImageAnalysis
                            .STRATEGY_KEEP_ONLY_LATEST
                    )
                    .build()

            analysis.setAnalyzer(
                cameraExecutor,
                NexusCameraAnalyzer(
                    pipeline =
                        pipeline,
                    scope =
                        scope,
                    requestProvider =
                        requestProvider,
                    onResult =
                        onResult
                )
            )

            analysisUseCase =
                analysis

            provider.unbindAll()

            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )

        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {

        analysisUseCase?.clearAnalyzer()

        analysisUseCase = null

        cameraExecutor.shutdown()
    }
}
