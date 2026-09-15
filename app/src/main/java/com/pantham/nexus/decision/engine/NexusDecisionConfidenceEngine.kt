package com.pantham.nexus.decision.engine

import com.pantham.nexus.decision.model.*

class NexusDecisionConfidenceEngine {

    fun calculate(
        analysis:
            DecisionAnalysis
    ): DecisionConfidence {

        val recommendation =
            analysis.recommendation
                ?: return DecisionConfidence.VERY_LOW

        val evidenceQuality =
            averageEvidence(
                analysis.request
            )

        val risk =
            recommendation.risks
                .maxOfOrNull {
                    it.severity
                } ?: 0f

        val missing =
            analysis.missingInformation
                .sumOf {
                    it.importance.toDouble()
                }
                .toFloat()
                .coerceAtMost(1f)

        val value =
            (
                recommendation.score * 0.55f +
                evidenceQuality * 0.30f -
                risk * 0.10f -
                missing * 0.05f
            ).coerceIn(0f, 1f)

        return when {
            value >= 0.85f ->
                DecisionConfidence.VERY_HIGH

            value >= 0.70f ->
                DecisionConfidence.HIGH

            value >= 0.50f ->
                DecisionConfidence.MEDIUM

            value >= 0.30f ->
                DecisionConfidence.LOW

            else ->
                DecisionConfidence.VERY_LOW
        }
    }


    private fun averageEvidence(
        request: DecisionRequest
    ): Float {

        val evidence =
            request.evidence +
                request.options.flatMap {
                    it.evidence
                }

        if (evidence.isEmpty()) {
            return 0.25f
        }

        return evidence
            .map {
                it.strength
            }
            .average()
            .toFloat()
            .coerceIn(0f, 1f)
    }
}
