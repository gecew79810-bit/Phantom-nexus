package com.pantham.nexus.vision

import android.graphics.Bitmap
import com.pantham.nexus.vision.model.VisionAnalysisResult
import com.pantham.nexus.vision.model.VisionFrame
import com.pantham.nexus.vision.model.VisionRequest

interface NexusVisionAnalyzer {

    suspend fun analyze(
        frame: VisionFrame,
        bitmap: Bitmap,
        request: VisionRequest
    ): VisionAnalysisResult
}
