package com.pantham.nexus.vision.screen

import android.graphics.Bitmap
import com.pantham.nexus.vision.model.VisionAnalysisResult
import com.pantham.nexus.vision.model.VisionFrame
import com.pantham.nexus.vision.model.VisionRequest
import com.pantham.nexus.vision.model.VisionSource
import com.pantham.nexus.vision.NexusVisionAnalyzer

class NexusScreenVisionEngine(
    private val analyzer: NexusVisionAnalyzer
) {

    suspend fun analyzeScreenshot(
        bitmap: Bitmap,
        instruction: String? = null
    ): VisionAnalysisResult {

        val frame =
            VisionFrame(
                source =
                    VisionSource.SCREEN,
                width =
                    bitmap.width,
                height =
                    bitmap.height,
                rotationDegrees =
                    0
            )

        return analyzer.analyze(
            frame =
                frame,
            bitmap =
                bitmap,
            request =
                VisionRequest(
                    type =
                        com.pantham.nexus.vision.model
                            .VisionTaskType.SCREEN_ANALYSIS,
                    source =
                        VisionSource.SCREEN,
                    userInstruction =
                        instruction,
                    needOCR =
                        true,
                    needObjects =
                        true,
                    needLabels =
                        true,
                    needBarcodes =
                        true,
                    needAIReasoning =
                        true
                )
        )
    }
}
