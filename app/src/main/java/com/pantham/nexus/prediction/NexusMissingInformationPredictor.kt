package com.pantham.nexus.prediction

data class ActionRequirement(
    val action: String,
    val requiredSlots: Set<String>,
    val availableSlots: Map<String, String>
)

class NexusMissingInformationPredictor(
    private val confidenceEngine:
        NexusPredictionConfidenceEngine
) {

    fun predict(
        requirement: ActionRequirement
    ): NexusPrediction? {

        val missing =
            requirement.requiredSlots
                .filter {
                    requirement
                        .availableSlots[it]
                        .isNullOrBlank()
                }
                .toSet()

        if (missing.isEmpty()) {
            return null
        }

        val evidence =
            listOf(
                PredictionEvidence(
                    source =
                        PredictionSource.CURRENT_CONTEXT,
                    description =
                        "Required action slots are missing.",
                    weight = 1f
                )
            )

        val score =
            confidenceEngine.calculate(
                evidence = evidence,
                contextMatch = 1f
            )

        return NexusPrediction(
            type =
                PredictionType.MISSING_INFORMATION,
            title =
                "Missing information",
            description =
                "More information is required before '${requirement.action}' can be completed.",
            predictedAction =
                requirement.action,
            requiredSlots =
                requirement.requiredSlots,
            missingSlots =
                missing,
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
