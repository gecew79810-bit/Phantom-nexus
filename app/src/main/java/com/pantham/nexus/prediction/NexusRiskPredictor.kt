package com.pantham.nexus.prediction

data class PlannedActionRiskInput(
    val action: String,

    val requiresNetwork: Boolean = false,
    val networkAvailable: Boolean = true,

    val requiresPermission: Boolean = false,
    val permissionGranted: Boolean = true,

    val destructive: Boolean = false,
    val irreversible: Boolean = false,

    val requiredSlotsMissing: Boolean = false,

    val toolHealthy: Boolean = true
)

class NexusRiskPredictor {

    fun predict(
        input: PlannedActionRiskInput
    ): NexusPrediction? {

        val reasons =
            mutableListOf<String>()

        var risk =
            RiskLevel.NONE

        if (
            input.requiresNetwork &&
            !input.networkAvailable
        ) {
            reasons +=
                "Required network is unavailable."

            risk = maxRisk(
                risk,
                RiskLevel.MEDIUM
            )
        }

        if (
            input.requiresPermission &&
            !input.permissionGranted
        ) {
            reasons +=
                "Required permission is missing."

            risk = maxRisk(
                risk,
                RiskLevel.HIGH
            )
        }

        if (input.requiredSlotsMissing) {
            reasons +=
                "Required information is missing."

            risk = maxRisk(
                risk,
                RiskLevel.MEDIUM
            )
        }

        if (!input.toolHealthy) {
            reasons +=
                "Required tool is unavailable or unhealthy."

            risk = maxRisk(
                risk,
                RiskLevel.HIGH
            )
        }

        if (input.destructive) {
            reasons +=
                "Action may modify or delete user data."

            risk = maxRisk(
                risk,
                RiskLevel.HIGH
            )
        }

        if (input.irreversible) {
            reasons +=
                "Action may be irreversible."

            risk = maxRisk(
                risk,
                RiskLevel.CRITICAL
            )
        }

        if (risk == RiskLevel.NONE) {
            return null
        }

        val score =
            when (risk) {
                RiskLevel.NONE -> 0f
                RiskLevel.LOW -> 0.35f
                RiskLevel.MEDIUM -> 0.60f
                RiskLevel.HIGH -> 0.82f
                RiskLevel.CRITICAL -> 0.97f
            }

        return NexusPrediction(
            type =
                PredictionType.RISK,
            title =
                "Execution risk detected",
            description =
                reasons.joinToString(" "),
            predictedAction =
                input.action,
            confidence =
                PredictionConfidence.HIGH,
            confidenceScore =
                score,
            disposition =
                if (
                    risk == RiskLevel.HIGH ||
                    risk == RiskLevel.CRITICAL
                ) {
                    PredictionDisposition
                        .REQUIRE_CONFIRMATION
                } else {
                    PredictionDisposition
                        .INTERNAL_ONLY
                },
            riskLevel =
                risk,
            evidence =
                reasons.map {
                    PredictionEvidence(
                        source =
                            PredictionSource.CURRENT_CONTEXT,
                        description = it,
                        weight = score
                    )
                }
        )
    }

    private fun maxRisk(
        first: RiskLevel,
        second: RiskLevel
    ): RiskLevel =
        if (first.ordinal >= second.ordinal) {
            first
        } else {
            second
        }
}
