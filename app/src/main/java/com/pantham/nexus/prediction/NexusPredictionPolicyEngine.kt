package com.pantham.nexus.prediction

class NexusPredictionPolicyEngine {

    fun evaluate(
        prediction: NexusPrediction
    ): PredictionPolicyDecision {

        if (
            prediction.confidenceScore < 0.30f
        ) {
            return PredictionPolicyDecision(
                prediction,
                PredictionDisposition.IGNORE,
                "Prediction confidence is too low."
            )
        }

        if (
            prediction.riskLevel ==
            RiskLevel.CRITICAL
        ) {
            return PredictionPolicyDecision(
                prediction,
                PredictionDisposition
                    .REQUIRE_CONFIRMATION,
                "Critical-risk predicted actions must never execute silently."
            )
        }

        if (
            prediction.riskLevel ==
            RiskLevel.HIGH
        ) {
            return PredictionPolicyDecision(
                prediction,
                PredictionDisposition
                    .REQUIRE_CONFIRMATION,
                "High-risk predicted action requires confirmation."
            )
        }

        return when (prediction.type) {

            PredictionType.RISK,
            PredictionType.MISSING_INFORMATION ->

                PredictionPolicyDecision(
                    prediction,
                    PredictionDisposition
                        .INTERNAL_ONLY,
                    "Used internally by planning."
                )

            PredictionType.NEXT_INTENT,
            PredictionType.GOAL_STEP,
            PredictionType.CONTEXT_CHANGE ->

                PredictionPolicyDecision(
                    prediction,
                    PredictionDisposition
                        .INTERNAL_ONLY,
                    "Context prediction should augment reasoning without bothering the user."
                )

            PredictionType.ROUTINE ->

                PredictionPolicyDecision(
                    prediction,
                    if (
                        prediction.confidenceScore >=
                        0.80f
                    ) {
                        PredictionDisposition.SUGGEST
                    } else {
                        PredictionDisposition
                            .INTERNAL_ONLY
                    },
                    "Routine predictions require strong evidence before proactive display."
                )

            PredictionType.NEXT_ACTION,
            PredictionType.REMINDER,
            PredictionType.PREPARATION,
            PredictionType.FOLLOW_UP,
            PredictionType.PROACTIVE_SUGGESTION ->

                PredictionPolicyDecision(
                    prediction,
                    if (
                        prediction.confidenceScore >=
                        0.60f
                    ) {
                        PredictionDisposition.SUGGEST
                    } else {
                        PredictionDisposition
                            .INTERNAL_ONLY
                    },
                    "Proactive suggestions require sufficient confidence."
                )
        }
    }
}
