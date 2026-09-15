package com.pantham.nexus.learning.repository

import com.pantham.nexus.learning.model.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryLearningRepository : NexusLearningRepository {

    private val mutex = Mutex()

    private val signals =
        ArrayDeque<LearningSignal>()

    private val feedback =
        ArrayDeque<DecisionFeedback>()

    private val preferences =
        LinkedHashMap<String, LearnedPreference>()

    private val adjustments =
        LinkedHashMap<String, StrategyAdjustment>()


    override suspend fun saveSignal(
        signal: LearningSignal
    ) {
        mutex.withLock {
            signals.addLast(signal)
            trimSignals()
        }
    }


    override suspend fun saveFeedback(
        feedback: DecisionFeedback
    ) {
        mutex.withLock {
            this.feedback.addLast(feedback)
            while (this.feedback.size > 100) {
                this.feedback.removeFirst()
            }
        }
    }


    override suspend fun savePreference(
        preference: LearnedPreference
    ) {
        mutex.withLock {
            preferences[buildPreferenceKey(preference)] = preference

            while (preferences.size > 100) {
                val first = preferences.entries.firstOrNull()?.key
                if (first != null) {
                    preferences.remove(first)
                } else {
                    break
                }
            }
        }
    }


    override suspend fun saveAdjustment(
        adjustment: StrategyAdjustment
    ) {
        mutex.withLock {
            adjustments[adjustment.key] = adjustment

            while (adjustments.size > 100) {
                val first = adjustments.keys.firstOrNull()
                if (first != null) {
                    adjustments.remove(first)
                } else {
                    break
                }
            }
        }
    }


    override suspend fun getSignals(
        limit: Int
    ): List<LearningSignal> =
        mutex.withLock {
            signals
                .takeLast(limit.coerceIn(1, 100))
                .toList()
        }


    override suspend fun getFeedback(
        limit: Int
    ): List<DecisionFeedback> =
        mutex.withLock {
            feedback
                .takeLast(limit.coerceIn(1, 100))
                .toList()
        }


    override suspend fun getPreferences(
        query: LearningQuery?
    ): List<LearnedPreference> =
        mutex.withLock {
            preferences.values
                .filter { preference ->
                    val keyMatch =
                        query?.key?.let {
                            preference.key.equals(it, ignoreCase = true)
                        } ?: true

                    val scopeMatch =
                        query?.scope?.let {
                            preference.scope == it
                        } ?: true

                    keyMatch && scopeMatch
                }
                .toList()
        }


    override suspend fun getAdjustments(
        limit: Int
    ): List<StrategyAdjustment> =
        mutex.withLock {
            adjustments.values
                .toList()
                .takeLast(limit.coerceIn(1, 100))
        }


    private fun buildPreferenceKey(
        preference: LearnedPreference
    ): String =
        "${preference.scope}:${preference.key}:${preference.value}"


    private fun trimSignals() {
        while (signals.size > 200) {
            signals.removeFirst()
        }
    }
}
