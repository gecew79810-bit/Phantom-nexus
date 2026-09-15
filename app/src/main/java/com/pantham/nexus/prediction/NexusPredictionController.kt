package com.pantham.nexus.prediction

class NexusPredictionController(
    private val repository:
        NexusPredictionRepository
) {

    private val engine =
        NexusPredictionEngine(
            repository
        )

    private val policy =
        NexusPredictionPolicyEngine()

    private val throttle =
        NexusSuggestionThrottle(
            repository
        )

    private val suggestionEngine =
        NexusProactiveSuggestionEngine(
            policy,
            throttle
        )

    private val feedbackEngine =
        NexusPredictionFeedbackEngine(
            repository
        )

    val contextAdapter =
        NexusPredictiveContextAdapter()

    val actionBridge =
        NexusPredictiveActionBridge(
            policy
        )

    suspend fun process(
        request: PredictionRequest
    ): PredictiveContext {

        val batch =
            engine.predict(request)

        return contextAdapter.build(
            batch
        )
    }

    suspend fun suggestions(
        predictions:
            List<NexusPrediction>
    ): List<ProactiveSuggestion> {

        return suggestionEngine
            .buildSuggestions(
                predictions
            )
    }

    suspend fun feedback(
        predictionId: String,
        feedback:
            PredictionFeedback,
        correction: String? = null
    ) {

        feedbackEngine.record(
            predictionId,
            feedback,
            correction
        )
    }

    suspend fun userAcceptanceRate():
        Float =
        feedbackEngine
            .acceptanceRate()
}
