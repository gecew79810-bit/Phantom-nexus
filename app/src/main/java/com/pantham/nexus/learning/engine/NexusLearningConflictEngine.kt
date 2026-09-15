package com.pantham.nexus.learning.engine

import com.pantham.nexus.learning.model.*

class NexusLearningConflictEngine {

    fun hasConflict(
        incoming: LearningSignal,
        existing: List<LearningSignal>
    ): Boolean {

        return existing.any {
            it.key.equals(incoming.key, ignoreCase = true) &&
            !it.value.equals(incoming.value, ignoreCase = true) &&
            it.confidence >= 0.70f &&
            incoming.confidence >= 0.70f
        }
    }

    fun chooseStronger(
        first: LearningSignal,
        second: LearningSignal
    ): LearningSignal {

        val firstScore =
            first.confidence * 0.65f +
                typePriority(first.type) * 0.35f

        val secondScore =
            second.confidence * 0.65f +
                typePriority(second.type) * 0.35f

        return if (firstScore >= secondScore) {
            first
        } else {
            second
        }
    }

    private fun typePriority(
        type: LearningSignalType
    ): Float =
        when (type) {
            LearningSignalType.EXPLICIT_PREFERENCE -> 1f
            LearningSignalType.USER_CORRECTION -> 1f
            LearningSignalType.USER_CHOICE -> 0.90f
            LearningSignalType.USER_REJECTION -> 0.90f
            LearningSignalType.SATISFACTION -> 0.90f
            LearningSignalType.DISSATISFACTION -> 0.90f
            LearningSignalType.OUTCOME_SUCCESS -> 0.85f
            LearningSignalType.OUTCOME_FAILURE -> 0.85f
            LearningSignalType.DECISION_OVERRIDE -> 0.80f
            LearningSignalType.OUTCOME_PARTIAL -> 0.75f
            LearningSignalType.IMPLICIT_PREFERENCE -> 0.40f
        }
}
