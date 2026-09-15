package com.pantham.nexus.learning.runtime

import com.pantham.nexus.learning.engine.*
import com.pantham.nexus.learning.model.*
import com.pantham.nexus.learning.repository.*
import com.pantham.nexus.learning.security.NexusLearningPrivacyGate

class NexusAdaptiveLearningRuntime private constructor() {

    private val repository: NexusLearningRepository =
        InMemoryLearningRepository()

    private val feedbackEngine =
        NexusLearningFeedbackEngine(repository)

    private val privacyGate =
        NexusLearningPrivacyGate()

    private val queryEngine =
        NexusLearningQueryEngine(repository)

    suspend fun recordSignal(
        signal: LearningSignal,
        outcome: DecisionOutcome? = null
    ): LearningUpdateResult? {

        val sanitized =
            privacyGate.sanitize(signal) ?: return null

        return feedbackEngine.record(
            signal = sanitized,
            outcome = outcome
        )
    }

    suspend fun getPreferences(): List<LearnedPreference> {
        return queryEngine.getStrongPreferences()
    }

    suspend fun getPreference(
        key: String
    ): LearnedPreference? {
        return queryEngine.getPreference(key)
    }

    suspend fun shouldPrefer(
        key: String,
        value: String
    ): Boolean {
        return queryEngine.shouldPrefer(key, value)
    }

    suspend fun getContext(): AdaptiveLearningContext {
        return AdaptiveLearningContext(
            preferences = repository.getPreferences().takeLast(50),
            adjustments = repository.getAdjustments(50),
            recentFeedback = repository.getFeedback(50)
        )
    }

    companion object {

        @Volatile
        private var INSTANCE: NexusAdaptiveLearningRuntime? = null

        fun getInstance(): NexusAdaptiveLearningRuntime {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NexusAdaptiveLearningRuntime().also {
                    INSTANCE = it
                }
            }
        }
    }
}
