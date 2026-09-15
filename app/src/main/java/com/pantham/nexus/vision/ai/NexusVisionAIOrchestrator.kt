package com.pantham.nexus.vision.ai

import android.graphics.Bitmap
import com.pantham.nexus.vision.model.VisionAnalysisResult

interface NexusMultimodalModel {

    suspend fun describeImage(
        bitmap: Bitmap,
        instruction: String?,
        structuredContext: String
    ): MultimodalVisionResponse
}

data class MultimodalVisionResponse(
    val description: String,
    val confidence: Float? = null,
    val suggestedActions: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

class NexusVisionAIOrchestrator(
    private val model: NexusMultimodalModel
) {

    suspend fun enrich(
        bitmap: Bitmap,
        instruction: String?,
        result: VisionAnalysisResult
    ): VisionAnalysisResult {

        val context =
            buildStructuredContext(
                result
            )

        val response =
            model.describeImage(
                bitmap =
                    bitmap,
                instruction =
                    instruction,
                structuredContext =
                    context
            )

        return result.copy(
            naturalDescription =
                response.description,
            warnings =
                result.warnings +
                    response.warnings
        )
    }

    private fun buildStructuredContext(
        result: VisionAnalysisResult
    ): String {

        return buildString {

            append(
                "Detected text:\n"
            )

            append(
                result.fullText
            )

            append(
                "\n\nObjects:\n"
            )

            result.objects
                .forEach {

                    append(
                        "${it.label} " +
                        "confidence=${it.confidence}\n"
                    )
                }

            append(
                "\nLabels:\n"
            )

            result.labels
                .forEach {

                    append(
                        "${it.label} " +
                        "confidence=${it.confidence}\n"
                    )
                }

            append(
                "\nBarcodes:\n"
            )

            result.barcodes
                .forEach {

                    append(
                        "${it.displayValue ?: it.rawValue}\n"
                    )
                }
        }
    }
}
