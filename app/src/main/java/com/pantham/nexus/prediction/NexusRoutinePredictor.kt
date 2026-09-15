package com.pantham.nexus.prediction

class NexusRoutinePredictor(
    private val confidenceEngine:
        NexusPredictionConfidenceEngine
) {

    fun predict(
        patterns: List<BehaviorPattern>
    ): List<NexusPrediction> {

        return patterns
            .filter {
                it.occurrences >= 3 &&
                    it.strength >= 0.50f
            }
            .take(5)
            .map { pattern ->

                val evidence =
                    listOf(
                        PredictionEvidence(
                            source =
                                PredictionSource.TEMPORAL_PATTERN,
                            description =
                                "Action '${pattern.action}' occurred ${pattern.occurrences} times.",
                            weight =
                                pattern.strength
                        ),
                        PredictionEvidence(
                            source =
                                PredictionSource.ACTION_HISTORY,
                            description =
                                "Historical success rate: ${pattern.successRate}.",
                            weight =
                                pattern.successRate
                        )
                    )

                val score =
                    confidenceEngine.calculate(
                        evidence = evidence,
                        patternStrength =
                            pattern.strength,
                        historicalAcceptance =
                            pattern.successRate
                    )

                NexusPrediction(
                    type =
                        PredictionType.ROUTINE,
                    title =
                        "Possible routine",
                    description =
                        "The user may repeat '${pattern.action}'.",
                    predictedAction =
                        pattern.action,
                    confidence =
                        confidenceEngine
                            .toConfidence(score),
                    confidenceScore =
                        score,
                    disposition =
                        PredictionDisposition.INTERNAL_ONLY,
                    evidence =
                        evidence
                )
            }
    }
}
