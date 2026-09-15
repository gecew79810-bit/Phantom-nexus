package com.pantham.nexus.decision.security

import com.pantham.nexus.decision.model.*

class NexusDecisionSafetyGate {

    fun evaluate(
        analysis: DecisionAnalysis
    ): Boolean {

        val criticalRisk =
            analysis.risks.any {
                it.severity >= 0.90f
            }

        val blocked =
            analysis.missingInformation.any {
                it.blocking
            }

        if (blocked) {
            return false
        }

        /*
         * A decision recommendation can still be returned
         * when risk is high, but it must be clearly labelled.
         *
         * This gate only prevents the Decision Core from
         * presenting an unsupported "certain" recommendation.
         */

        if (
            criticalRisk &&
            analysis.confidence >=
            DecisionConfidence.HIGH
        ) {
            return false
        }

        return true
    }


    fun riskLevel(
        analysis: DecisionAnalysis
    ): DecisionRisk {

        val severity =
            analysis.risks
                .maxOfOrNull {
                    it.severity
                } ?: 0f

        return when {
            severity >= 0.90f ->
                DecisionRisk.CRITICAL

            severity >= 0.70f ->
                DecisionRisk.HIGH

            severity >= 0.45f ->
                DecisionRisk.MEDIUM

            severity >= 0.20f ->
                DecisionRisk.LOW

            else ->
                DecisionRisk.MINIMAL
        }
    }
}
