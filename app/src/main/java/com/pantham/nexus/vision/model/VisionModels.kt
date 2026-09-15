package com.pantham.nexus.vision.model

import android.graphics.Rect
import java.util.UUID

enum class VisionSource {
    CAMERA,
    SCREEN,
    IMAGE_FILE,
    VIDEO_FRAME,
    VISION_ATTACHMENT
}

enum class VisionTaskType {
    OCR,
    OBJECT_DETECTION,
    IMAGE_LABELING,
    BARCODE,
    SCREEN_ANALYSIS,
    VISUAL_COMPARISON,
    IMAGE_DESCRIPTION,
    GENERAL_VISION
}

enum class VisionConfidence {
    LOW,
    MEDIUM,
    HIGH
}

data class VisionBoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    fun toRect(): Rect =
        Rect(
            left,
            top,
            right,
            bottom
        )
}

data class VisionTextBlock(
    val text: String,
    val boundingBox: VisionBoundingBox? = null,
    val confidence: Float? = null
)

data class VisionObject(
    val label: String,
    val confidence: Float,
    val boundingBox: VisionBoundingBox? = null,
    val trackingId: Int? = null
)

data class VisionBarcode(
    val rawValue: String?,
    val displayValue: String?,
    val format: Int,
    val boundingBox: VisionBoundingBox? = null
)

data class VisionLabel(
    val label: String,
    val confidence: Float
)

data class VisionFrame(
    val id: String = UUID.randomUUID().toString(),
    val source: VisionSource,
    val timestamp: Long = System.currentTimeMillis(),
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val imageUri: String? = null
)

data class VisionAnalysisResult(
    val frameId: String,
    val source: VisionSource,
    val taskType: VisionTaskType,
    val processingTimeMs: Long,
    val fullText: String = "",
    val textBlocks: List<VisionTextBlock> = emptyList(),
    val objects: List<VisionObject> = emptyList(),
    val labels: List<VisionLabel> = emptyList(),
    val barcodes: List<VisionBarcode> = emptyList(),
    val screenDescription: String? = null,
    val naturalDescription: String? = null,
    val confidence: VisionConfidence = VisionConfidence.MEDIUM,
    val warnings: List<String> = emptyList()
)

data class VisualContext(
    val frameId: String,
    val source: VisionSource,
    val description: String?,
    val visibleText: String,
    val detectedObjects: List<VisionObject>,
    val detectedLabels: List<VisionLabel>,
    val barcodes: List<VisionBarcode>,
    val timestamp: Long
)

data class VisualComparisonResult(
    val similarity: Float,
    val changed: Boolean,
    val changedRegions: List<VisionBoundingBox>,
    val summary: String
)

data class VisionRequest(
    val type: VisionTaskType,
    val source: VisionSource,
    val userInstruction: String? = null,
    val needOCR: Boolean = true,
    val needObjects: Boolean = true,
    val needLabels: Boolean = false,
    val needBarcodes: Boolean = true,
    val needAIReasoning: Boolean = true
)
