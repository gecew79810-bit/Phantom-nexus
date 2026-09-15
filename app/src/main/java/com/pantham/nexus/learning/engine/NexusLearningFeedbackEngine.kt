package com.pantham.nexus.learning.engine

import com.pantham.nexus.learning.model.*
import com.pantham.nexus.learning.repository.NexusLearningRepository

class NexusLearningFeedbackEngine(
    private val repository: NexusLearningRepository
) {

    private val conflictEngine =
        NexusLearningConflictEngine()

    private val preferenceEngine =
        NexusPreferenceLearningEngine()

    private val strategyEngine =
        NexusStrategyLearningEngine()

    suspend fun record(
        signal: LearningSignal,
        outcome: DecisionOutcome? = null
    ): LearningUpdateResult {

        val previous = repository.getSignals(200)

        val feedback =
            DecisionFeedback(
                decisionId = outcome?.decisionId ?: signal.id,
                signal = signal,
                outcome = outcome,
                explanation = buildExplanation(signal, outcome)
            )

        repository.saveSignal(signal)
        repository.saveFeedback(feedback)

        val ignored = mutableListOf<LearningSignal>()

        val conflict = conflictEngine.hasConflict(signal, previous)

        if (conflict) {
            val conflicting =
                previous
                    .filter {
                        it.key.equals(signal.key, ignoreCase = true)
                    }
                    .maxByOrNull {
                        it.confidence
                    }

            if (conflicting != null) {
                val stronger =
                    conflictEngine.chooseStronger(signal, conflicting)

                if (stronger.id != signal.id) {
                    ignored += signal
                }
            }
        }

        val existingPreferences = repository.getPreferences()

        val learned =
            if (ignored.isEmpty()) {
                preferenceEngine.learn(
                    signal = signal,
                    existing = existingPreferences,
                    historicalSignals = previous
                )
            } else {
                null
            }

        val learnedList =
            if (learned != null) {
                repository.savePreference(learned)
                listOf(learned)
            } else {
                emptyList()
            }

        val adjustments = mutableListOf<StrategyAdjustment>()

        if (outcome != null && outcome.status != OutcomeStatus.UNKNOWN) {
            val existingAdjustments = repository.getAdjustments()

            val old =
                existingAdjustments
                    .firstOrNull {
                        it.key == signal.key
                    }
                    ?.newWeight
                    ?: 0.50f

            val adjustment =
                strategyEngine.adjust(
                    key = signal.key,
                    oldWeight = old,
                    outcome = outcome.status,
                    confidence = signal.confidence
                )

            repository.saveAdjustment(adjustment)
            adjustments += adjustment
        }

        val finalConfidence =
            mapConfidence(
                (signal.confidence + (learned?.strength ?: 0f)) / 2f
            )

        return LearningUpdateResult(
            feedbackId = feedback.decisionId,
            learnedPreferences = learnedList,
            strategyAdjustments = adjustments,
            ignoredSignals = ignored,
            confidence = finalConfidence
        )
    }

    private fun buildExplanation(
        signal: LearningSignal,
        outcome: DecisionOutcome?
    ): String {

        if (outcome != null) {
            return when (outcome.status) {
                OutcomeStatus.SUCCESS ->
                    "Observed successful decision outcome."
                OutcomeStatus.FAILURE ->
                    "Observed failed decision outcome."
                OutcomeStatus.PARTIAL ->
                    "Observed partially successful decision outcome."
                OutcomeStatus.CANCELLED ->
                    "Decision was cancelled."
                OutcomeStatus.UNKNOWN ->
                    "Outcome was recorded as unknown."
            }
        }

        return when (signal.type) {
            LearningSignalType.USER_CHOICE ->
                "User selected this option."
            LearningSignalType.USER_REJECTION ->
                "User rejected this option."
            LearningSignalType.USER_CORRECTION ->
                "User explicitly corrected the assistant."
            LearningSignalType.EXPLICIT_PREFERENCE ->
                "User explicitly stated a preference."
            LearningSignalType.IMPLICIT_PREFERENCE ->
                "Preference inferred from user behavior."
            LearningSignalType.SATISFACTION ->
                "User expressed satisfaction."
            LearningSignalType.DISSATISFACTION ->
                "User expressed dissatisfaction."
            LearningSignalType.DECISION_OVERRIDE ->
                "User overrode the assistant recommendation."
            else ->
                "Decision feedback recorded."
        }
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
