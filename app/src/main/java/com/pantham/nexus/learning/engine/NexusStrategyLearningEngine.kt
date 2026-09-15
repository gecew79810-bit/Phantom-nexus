package com.pantham.nexus.learning.engine

import com.pantham.nexus.learning.model.*

class NexusStrategyLearningEngine {

    fun adjust(
        key: String,
        oldWeight: Float,
        outcome: OutcomeStatus,
        confidence: Float
    ): StrategyAdjustment {

        val delta =
            when (outcome) {
                OutcomeStatus.SUCCESS -> 0.05f
                OutcomeStatus.FAILURE -> -0.07f
                OutcomeStatus.PARTIAL -> -0.025f
                OutcomeStatus.CANCELLED -> -0.01f
                OutcomeStatus.UNKNOWN -> 0f
            }

        val adjustedDelta =
            delta * confidence

        val newWeight =
            (oldWeight + adjustedDelta).coerceIn(0f, 1f)

        val reason =
            when (outcome) {
                OutcomeStatus.SUCCESS ->
                    "Successful outcome supports this strategy."
                OutcomeStatus.FAILURE ->
                    "Failed outcome suggests reducing reliance on this strategy."
                OutcomeStatus.PARTIAL ->
                    "Partial outcome suggests a small strategy correction."
                OutcomeStatus.CANCELLED ->
                    "Cancelled outcome provides weak negative feedback."
                OutcomeStatus.UNKNOWN ->
                    "No reliable outcome information available."
            }

        return StrategyAdjustment(
            key = key,
            oldWeight = oldWeight,
            newWeight = newWeight,
            reason = reason,
            confidence = mapConfidence(confidence)
        )
    }

    private fun mapConfidence(
        value: Float
    ): LearningConfidence =
        when {
            value >= 0.85f -> LearningConfidence.VERY_HIGH
            value >= 0.70f -> LearningConfidence.HIGH
            value >= 0.50f -> LearningConfidence.MEDIUM
            value >= 0.30f -> LearningConfidence.LOW
            else -> LearningConfidence.VERY_LOW
        }
}
