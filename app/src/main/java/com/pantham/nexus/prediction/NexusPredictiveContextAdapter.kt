package com.pantham.nexus.prediction

data class PredictiveContext(
    val predictions:
        List<NexusPrediction>,

    val internalHints:
        List<String>,

    val proactiveCandidates:
        List<NexusPrediction>
)

class NexusPredictiveContextAdapter {

    fun build(
        batch: PredictionBatch
    ): PredictiveContext {

        val internal =
            batch.predictions
                .filter {
                    it.disposition ==
                        PredictionDisposition
                            .INTERNAL_ONLY ||
                    it.type ==
                        PredictionType
                            .MISSING_INFORMATION ||
                    it.type ==
                        PredictionType.RISK
                }
                .take(8)

        val proactive =
            batch.predictions
                .filter {
                    it.type ==
                        PredictionType.PREPARATION ||
                    it.type ==
                        PredictionType.ROUTINE ||
                    it.type ==
                        PredictionType.REMINDER ||
                    it.type ==
                        PredictionType
                            .PROACTIVE_SUGGESTION
                }
                .take(5)

        return PredictiveContext(
            predictions =
                batch.predictions,
            internalHints =
                internal.map {
                    buildString {
                        append(it.type.name)
                        append(": ")
                        append(it.description)
                        append(
                            " [confidence="
                        )
                        append(
                            it.confidenceScore
                        )
                        append("]")
                    }
                },
            proactiveCandidates =
                proactive
        )
    }
}
