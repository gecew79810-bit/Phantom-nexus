package com.pantham.nexus.vision.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.pantham.nexus.vision.model.InputImageConverter
import com.pantham.nexus.vision.model.VisionFrame
import com.pantham.nexus.vision.model.VisionRequest
import com.pantham.nexus.vision.pipeline.NexusVisionPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NexusCameraAnalyzer(
    private val pipeline: NexusVisionPipeline,
    private val scope: CoroutineScope,
    private val requestProvider:
        () -> VisionRequest,
    private val onResult:
        (com.pantham.nexus.vision.model.VisionAnalysisResult) -> Unit
) : ImageAnalysis.Analyzer {

    private var processing =
        false

    override fun analyze(
        imageProxy: ImageProxy
    ) {

        if (processing) {
            imageProxy.close()
            return
        }

        processing = true

        val rotation =
            imageProxy.imageInfo.rotationDegrees

        scope.launch(Dispatchers.Default) {

            try {

                val bitmap =
                    InputImageConverter
                        .imageProxyToBitmap(
                            imageProxy
                        )

                if (bitmap == null) {
                    return@launch
                }

                val frame =
                    VisionFrame(
                        source =
                            com.pantham.nexus.vision.model
                                .VisionSource.CAMERA,
                        width =
                            bitmap.width,
                        height =
                            bitmap.height,
                        rotationDegrees =
                            rotation
                    )

                val result =
                    pipeline.analyze(
                        frame =
                            frame,
                        bitmap =
                            bitmap,
                        request =
                            requestProvider()
                    )

                onResult(result)

            } catch (
                throwable: Throwable
            ) {

                // Report to existing Diagnostics/Audit system.
                throwable.printStackTrace()

            } finally {

                processing = false

                imageProxy.close()
            }
        }
    }
}
