package com.pantham.nexus.prediction

data class PredictionRequest(
    val context: PredictionContext,

    val actionRequirement:
        ActionRequirement? = null,

    val upcomingEvents:
        List<UpcomingContextEvent> =
            emptyList(),

    val behaviorEvents:
        List<BehaviorEvent> =
            emptyList(),

    val plannedRiskInput:
        PlannedActionRiskInput? = null
)

class NexusPredictionEngine(
    private val repository:
        NexusPredictionRepository
) {

    private val confidence =
        NexusPredictionConfidenceEngine()

    private val nextIntent =
        NexusNextIntentPredictor(
            confidence
        )

    private val missingInfo =
        NexusMissingInformationPredictor(
            confidence
        )

    private val preparation =
        NexusPreparationPredictor(
            confidence
        )

    private val routine =
        NexusRoutinePredictor(
            confidence
        )

    private val patternEngine =
        NexusBehaviorPatternEngine()

    private val risk =
        NexusRiskPredictor()

    suspend fun predict(
        request: PredictionRequest
    ): PredictionBatch {

        repository.removeExpired()

        val predictions =
            mutableListOf<NexusPrediction>()

        predictions +=
            nextIntent.predict(
                request.context
            )

        request.actionRequirement
            ?.let {
                missingInfo.predict(it)
            }
            ?.let {
                predictions += it
            }

        request.upcomingEvents
            .forEach { event ->

                predictions +=
                    preparation.predict(
                        event,
                        request.context
                            .currentTimeMillis
                    )
            }

        if (
            request.behaviorEvents
                .isNotEmpty()
        ) {

            val patterns =
                patternEngine.detect(
                    request.behaviorEvents
                )

            predictions +=
                routine.predict(patterns)
        }

        request.plannedRiskInput
            ?.let {
                risk.predict(it)
            }
            ?.let {
                predictions += it
            }

        val ranked =
            predictions
                .distinctBy {
                    predictionKey(it)
                }
                .sortedByDescending {
                    it.confidenceScore
                }

        repository.savePredictions(
            ranked
        )

        return PredictionBatch(
            predictions = ranked
        )
    }

    private fun predictionKey(
        prediction: NexusPrediction
    ): String =
        listOf(
            prediction.type.name,
            prediction.predictedIntent ?: "",
            prediction.predictedAction ?: "",
            prediction.targetEntityId ?: "",
            prediction.relatedGoalId ?: ""
        ).joinToString("|")
}
