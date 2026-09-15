package com.pantham.nexus.vision.pipeline

import android.graphics.Bitmap
import com.pantham.nexus.vision.NexusVisionAnalyzer
import com.pantham.nexus.vision.barcode.NexusBarcodeEngine
import com.pantham.nexus.vision.labels.NexusImageLabelEngine
import com.pantham.nexus.vision.model.VisionAnalysisResult
import com.pantham.nexus.vision.model.VisionConfidence
import com.pantham.nexus.vision.model.VisionFrame
import com.pantham.nexus.vision.model.VisionRequest
import com.pantham.nexus.vision.objects.NexusObjectDetector
import com.pantham.nexus.vision.ocr.NexusTextRecognitionEngine
import kotlin.math.max

class NexusVisionPipeline(
    private val ocr: NexusTextRecognitionEngine,
    private val barcode: NexusBarcodeEngine,
    private val labels: NexusImageLabelEngine,
    private val objects: NexusObjectDetector
) : NexusVisionAnalyzer {

    override suspend fun analyze(
        frame: VisionFrame,
        bitmap: Bitmap,
        request: VisionRequest
    ): VisionAnalysisResult {

        val start =
            System.currentTimeMillis()

        var textBlocks =
            emptyList<com.pantham.nexus.vision.model.VisionTextBlock>()

        var detectedObjects =
            emptyList<com.pantham.nexus.vision.model.VisionObject>()

        var detectedLabels =
            emptyList<com.pantham.nexus.vision.model.VisionLabel>()

        var detectedBarcodes =
            emptyList<com.pantham.nexus.vision.model.VisionBarcode>()

        val warnings =
            mutableListOf<String>()

        if (request.needOCR) {

            runCatching {
                textBlocks =
                    ocr.recognize(
                        bitmap = bitmap,
                        rotationDegrees =
                            frame.rotationDegrees
                    )
            }.onFailure {
                warnings +=
                    "Text recognition failed."
            }
        }

        if (request.needBarcodes) {

            runCatching {
                detectedBarcodes =
                    barcode.scan(
                        bitmap = bitmap,
                        rotationDegrees =
                            frame.rotationDegrees
                    )
            }.onFailure {
                warnings +=
                    "Barcode scanning failed."
            }
        }

        if (request.needLabels) {

            runCatching {
                detectedLabels =
                    labels.detect(
                        bitmap = bitmap,
                        rotationDegrees =
                            frame.rotationDegrees
                    )
            }.onFailure {
                warnings +=
                    "Image labeling failed."
            }
        }

        if (request.needObjects) {

            runCatching {
                detectedObjects =
                    objects.detect(
                        bitmap = bitmap,
                        rotationDegrees =
                            frame.rotationDegrees
                    )
            }.onFailure {
                warnings +=
                    "Object detection failed."
            }
        }

        val fullText =
            textBlocks
                .joinToString("\n") {
                    it.text
                }

        val confidence =
            calculateConfidence(
                hasText =
                    textBlocks.isNotEmpty(),
                hasObjects =
                    detectedObjects.isNotEmpty(),
                hasLabels =
                    detectedLabels.isNotEmpty(),
                hasBarcodes =
                    detectedBarcodes.isNotEmpty(),
                hasWarnings =
                    warnings.isNotEmpty()
            )

        return VisionAnalysisResult(
            frameId = frame.id,
            source = frame.source,
            taskType = request.type,
            processingTimeMs =
                System.currentTimeMillis() - start,
            fullText = fullText,
            textBlocks = textBlocks,
            objects = detectedObjects,
            labels = detectedLabels,
            barcodes = detectedBarcodes,
            confidence = confidence,
            warnings = warnings
        )
    }

    private fun calculateConfidence(
        hasText: Boolean,
        hasObjects: Boolean,
        hasLabels: Boolean,
        hasBarcodes: Boolean,
        hasWarnings: Boolean
    ): VisionConfidence {

        val signals =
            listOf(
                hasText,
                hasObjects,
                hasLabels,
                hasBarcodes
            ).count { it }

        return when {
            hasWarnings &&
                signals <= 1 ->
                VisionConfidence.LOW

            signals >= 3 ->
                VisionConfidence.HIGH

            else ->
                VisionConfidence.MEDIUM
        }
    }

    fun close() {
        ocr.close()
        barcode.close()
        labels.close()
    }
}
