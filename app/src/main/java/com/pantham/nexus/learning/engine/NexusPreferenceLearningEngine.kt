package com.pantham.nexus.learning.engine

import com.pantham.nexus.learning.model.*

class NexusPreferenceLearningEngine {

    private val reliabilityEngine =
        NexusLearningReliabilityEngine()

    fun learn(
        signal: LearningSignal,
        existing: List<LearnedPreference>,
        historicalSignals: List<LearningSignal>
    ): LearnedPreference? {

        if (signal.confidence < 0.45f) {
            return null
        }

        val reliability =
            reliabilityEngine.reliability(signal)

        val evidence =
            reliabilityEngine.evidence(
                signal,
                historicalSignals
            )

        val strength =
            (
                reliability * 0.60f +
                evidence.overallStrength * 0.40f
            ).coerceIn(0f, 1f)

        val polarity =
            when (signal.type) {
                LearningSignalType.USER_REJECTION,
                LearningSignalType.DISSATISFACTION,
                LearningSignalType.OUTCOME_FAILURE ->
                    PreferencePolarity.DISLIKE

                LearningSignalType.USER_CHOICE,
                LearningSignalType.EXPLICIT_PREFERENCE,
                LearningSignalType.SATISFACTION,
                LearningSignalType.OUTCOME_SUCCESS ->
                    PreferencePolarity.PREFER

                else ->
                    PreferencePolarity.NEUTRAL
            }

        val same =
            existing.firstOrNull {
                it.key.equals(signal.key, ignoreCase = true) &&
                it.value.equals(signal.value, ignoreCase = true)
            }

        val evidenceCount =
            (same?.evidenceCount ?: 0) + 1

        val blendedStrength =
            if (same == null) {
                strength
            } else {
                (
                    same.strength * 0.65f +
                    strength * 0.35f
                ).coerceIn(0f, 1f)
            }

        return LearnedPreference(
            key = signal.key,
            value = signal.value,
            polarity = polarity,
            strength = blendedStrength,
            confidence = mapConfidence(blendedStrength),
            evidenceCount = evidenceCount,
            lastUpdated = System.currentTimeMillis(),
            scope = signal.scope
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
