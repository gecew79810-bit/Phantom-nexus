package com.pantham.nexus.decision.engine

import com.pantham.nexus.decision.model.*

class NexusDecisionEvidenceEngine {

    fun evaluateEvidence(
        option: DecisionOption
    ): Float {

        if (option.evidence.isEmpty()) {
            return 0.25f
        }

        val weighted =
            option.evidence.map {
                it.strength
            }

        return (
            weighted.average()
                .toFloat()
        ).coerceIn(0f, 1f)
    }


    fun evaluateEvidence(
        evidence: List<DecisionEvidence>
    ): Float {

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
