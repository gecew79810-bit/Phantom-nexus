package com.pantham.nexus.learning.engine

import com.pantham.nexus.learning.model.*

class NexusLearningReliabilityEngine {

    fun reliability(
        signal: LearningSignal
    ): Float {

        val base =
            when (signal.type) {
                LearningSignalType.USER_CHOICE -> 0.90f
                LearningSignalType.USER_CORRECTION -> 0.98f
                LearningSignalType.EXPLICIT_PREFERENCE -> 1.00f
                LearningSignalType.USER_REJECTION -> 0.85f
                LearningSignalType.OUTCOME_SUCCESS -> 0.92f
                LearningSignalType.OUTCOME_FAILURE -> 0.92f
                LearningSignalType.SATISFACTION -> 0.95f
                LearningSignalType.DISSATISFACTION -> 0.95f
                LearningSignalType.DECISION_OVERRIDE -> 0.90f
                LearningSignalType.OUTCOME_PARTIAL -> 0.80f
                LearningSignalType.IMPLICIT_PREFERENCE -> 0.45f
            }

        return (base * signal.confidence).coerceIn(0f, 1f)
    }

    fun evidence(
        signal: LearningSignal,
        previousSignals: List<LearningSignal>
    ): LearningEvidence {

        val sameKey =
            previousSignals.filter {
                it.key.equals(signal.key, ignoreCase = true)
            }

        val consistency =
            if (sameKey.isEmpty()) {
                0.50f
            } else {
                val matching =
                    sameKey.count {
                        it.value.equals(signal.value, ignoreCase = true)
                    }

                (matching.toFloat() / sameKey.size).coerceIn(0f, 1f)
            }

        val age =
            System.currentTimeMillis() - signal.timestamp

        val day =
            24L * 60L * 60L * 1000L

        val recency =
            (1f - (age.toFloat() / (30L * day))).coerceIn(0.10f, 1f)

        return LearningEvidence(
            sourceSignalId = signal.id,
            type = signal.type,
            reliability = reliability(signal),
            consistency = consistency,
            recency = recency
        )
    }
}
