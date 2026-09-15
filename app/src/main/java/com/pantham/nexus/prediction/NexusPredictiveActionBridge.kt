package com.pantham.nexus.prediction

data class PredictedActionCandidate(
    val predictionId: String,
    val actionName: String,
    val confidence: Float,
    val requiresConfirmation: Boolean,
    val metadata: Map<String, String>
)

class NexusPredictiveActionBridge(
    private val policy:
        NexusPredictionPolicyEngine
) {

    fun convert(
        prediction: NexusPrediction
    ): PredictedActionCandidate? {

        val action =
            prediction.predictedAction
                ?: return null

        val decision =
            policy.evaluate(prediction)

        if (
            decision.disposition ==
                PredictionDisposition.IGNORE ||
            decision.disposition ==
                PredictionDisposition
                    .INTERNAL_ONLY
        ) {
            return null
        }

        return PredictedActionCandidate(
            predictionId =
                prediction.id,
            actionName =
                action,
            confidence =
                prediction.confidenceScore,
            requiresConfirmation =
                decision.disposition ==
                    PredictionDisposition
                        .REQUIRE_CONFIRMATION ||
                    prediction.riskLevel >=
                        RiskLevel.HIGH,
            metadata =
                prediction.metadata
        )
    }
}
