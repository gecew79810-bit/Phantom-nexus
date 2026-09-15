package com.pantham.nexus.prediction

class NexusNextIntentPredictor(
    private val confidenceEngine:
        NexusPredictionConfidenceEngine
) {

    fun predict(
        context: PredictionContext
    ): List<NexusPrediction> {

        val output =
            mutableListOf<NexusPrediction>()

        predictFollowUp(context)
            ?.let(output::add)

        predictGoalContinuation(context)
            ?.let(output::add)

        return output
    }

    private fun predictFollowUp(
        context: PredictionContext
    ): NexusPrediction? {

        val intent =
            context.currentIntent
                ?: return null

        if (
            context.recentIntents.isEmpty()
        ) {
            return null
        }

        val repeated =
            context.recentIntents.count {
                it.equals(
                    intent,
                    ignoreCase = true
                )
            }

        if (repeated < 2) {
            return null
        }

        val evidence =
            listOf(
                PredictionEvidence(
                    source =
                        PredictionSource.CONVERSATION,
                    description =
                        "Current intent appeared repeatedly in recent turns.",
                    weight = 0.8f
                )
            )

        val score =
            confidenceEngine.calculate(
                evidence = evidence,
                patternStrength =
                    (repeated / 4f)
                        .coerceIn(0f, 1f),
                contextMatch = 0.8f
            )

        return NexusPrediction(
            type =
                PredictionType.FOLLOW_UP,
            title =
                "Likely follow-up",
            description =
                "User may continue the current intent.",
            predictedIntent =
                intent,
            confidence =
                confidenceEngine.toConfidence(
                    score
                ),
            confidenceScore =
                score,
            disposition =
                PredictionDisposition.INTERNAL_ONLY,
            evidence =
                evidence
        )
    }

    private fun predictGoalContinuation(
        context: PredictionContext
    ): NexusPrediction? {

        val goalId =
            context.activeGoalId
                ?: return null

        val evidence =
            listOf(
                PredictionEvidence(
                    source =
                        PredictionSource.CURRENT_CONTEXT,
                    description =
                        "An active goal is still present.",
                    weight = 0.9f
                )
            )

        val score =
            confidenceEngine.calculate(
                evidence = evidence,
                contextMatch = 0.85f
            )

        return NexusPrediction(
            type =
                PredictionType.GOAL_STEP,
            title =
                "Continue active goal",
            description =
                "The next request may relate to the active goal.",
            relatedGoalId =
                goalId,
            confidence =
                confidenceEngine.toConfidence(
                    score
                ),
            confidenceScore =
                score,
            disposition =
                PredictionDisposition.INTERNAL_ONLY,
            evidence =
                evidence
        )
    }
}
