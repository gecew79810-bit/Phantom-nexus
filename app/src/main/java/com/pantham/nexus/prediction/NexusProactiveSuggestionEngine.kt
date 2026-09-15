package com.pantham.nexus.prediction

data class ProactiveSuggestion(
    val predictionId: String,
    val title: String,
    val message: String,
    val action: String? = null,
    val requiresConfirmation: Boolean = false
)

class NexusProactiveSuggestionEngine(
    private val policy:
        NexusPredictionPolicyEngine,
    private val throttle:
        NexusSuggestionThrottle
) {

    suspend fun buildSuggestions(
        predictions: List<NexusPrediction>
    ): List<ProactiveSuggestion> {

        val output =
            mutableListOf<ProactiveSuggestion>()

        for (prediction in predictions) {

            val decision =
                policy.evaluate(prediction)

            if (
                decision.disposition !=
                    PredictionDisposition.SUGGEST &&
                decision.disposition !=
                    PredictionDisposition
                        .REQUIRE_CONFIRMATION
            ) {
                continue
            }

            if (
                !throttle.shouldShow(
                    prediction
                )
            ) {
                continue
            }

            output +=
                ProactiveSuggestion(
                    predictionId =
                        prediction.id,
                    title =
                        prediction.title,
                    message =
                        prediction.description,
                    action =
                        prediction.predictedAction,
                    requiresConfirmation =
                        decision.disposition ==
                            PredictionDisposition
                                .REQUIRE_CONFIRMATION
                )
        }

        return output
    }
}
