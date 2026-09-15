package com.pantham.nexus.learning.repository

import com.pantham.nexus.learning.model.*

interface NexusLearningRepository {

    suspend fun saveSignal(
        signal: LearningSignal
    )

    suspend fun saveFeedback(
        feedback: DecisionFeedback
    )

    suspend fun savePreference(
        preference: LearnedPreference
    )

    suspend fun saveAdjustment(
        adjustment: StrategyAdjustment
    )

    suspend fun getSignals(
        limit: Int = 100
    ): List<LearningSignal>

    suspend fun getFeedback(
        limit: Int = 100
    ): List<DecisionFeedback>

    suspend fun getPreferences(
        query: LearningQuery? = null
    ): List<LearnedPreference>

    suspend fun getAdjustments(
        limit: Int = 100
    ): List<StrategyAdjustment>
}
