package com.pantham.nexus.vision.context

import com.pantham.nexus.vision.model.VisualContext
import com.pantham.nexus.vision.model.VisionAnalysisResult

class NexusVisualContextBuilder {

    fun build(
        result: VisionAnalysisResult
    ): VisualContext {

        val description =
            buildString {

                if (
                    result.naturalDescription
                        ?.isNotBlank() == true
                ) {
                    append(
                        result.naturalDescription
                    )
                }

                if (
                    result.screenDescription
                        ?.isNotBlank() == true
                ) {

                    if (isNotEmpty()) {
                        append("\n")
                    }

                    append(
                        result.screenDescription
                    )
                }

                if (
                    result.objects.isNotEmpty()
                ) {

                    if (isNotEmpty()) {
                        append("\n")
                    }

                    append(
                        "Objects: "
                    )

                    append(
                        result.objects
                            .sortedByDescending {
                                it.confidence
                            }
                            .take(8)
                            .joinToString {
                                it.label
                            }
                    )
                }
            }

        return VisualContext(
            frameId =
                result.frameId,
            source =
                result.source,
            description =
                description.ifBlank {
                    null
                },
            visibleText =
                result.fullText,
            detectedObjects =
                result.objects,
            detectedLabels =
                result.labels,
            barcodes =
                result.barcodes,
            timestamp =
                System.currentTimeMillis()
        )
    }
}
