package com.pantham.nexus.learning.model

enum class LearningSignalType {
    USER_CHOICE,
    USER_REJECTION,
    USER_CORRECTION,
    OUTCOME_SUCCESS,
    OUTCOME_FAILURE,
    OUTCOME_PARTIAL,
    EXPLICIT_PREFERENCE,
    IMPLICIT_PREFERENCE,
    DECISION_OVERRIDE,
    SATISFACTION,
    DISSATISFACTION
}

enum class LearningConfidence {
    VERY_LOW,
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}

enum class LearningScope {
    SESSION,
    TASK,
    DECISION_TYPE,
    USER_PREFERENCE,
    GENERAL_STRATEGY
}

enum class OutcomeStatus {
    UNKNOWN,
    SUCCESS,
    FAILURE,
    PARTIAL,
    CANCELLED
}

enum class PreferencePolarity {
    PREFER,
    DISLIKE,
    NEUTRAL
}

data class LearningSignal(
    val id: String,
    val type: LearningSignalType,
    val key: String,
    val value: String,
    val confidence: Float,
    val scope: LearningScope,
    val source: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class DecisionOutcome(
    val decisionId: String,
    val status: OutcomeStatus,
    val selectedOptionId: String? = null,
    val actualResult: String? = null,
    val userSatisfied: Boolean? = null,
    val observedAt: Long = System.currentTimeMillis()
)

data class DecisionFeedback(
    val decisionId: String,
    val signal: LearningSignal,
    val outcome: DecisionOutcome? = null,
    val explanation: String = "",
    val recordedAt: Long = System.currentTimeMillis()
)

data class LearnedPreference(
    val key: String,
    val value: String,
    val polarity: PreferencePolarity,
    val strength: Float,
    val confidence: LearningConfidence,
    val evidenceCount: Int,
    val lastUpdated: Long,
    val scope: LearningScope
)

data class StrategyAdjustment(
    val key: String,
    val oldWeight: Float,
    val newWeight: Float,
    val reason: String,
    val confidence: LearningConfidence,
    val updatedAt: Long = System.currentTimeMillis()
)

data class LearningEvidence(
    val sourceSignalId: String,
    val type: LearningSignalType,
    val reliability: Float,
    val consistency: Float,
    val recency: Float
) {
    val overallStrength: Float
        get() = (
            reliability * 0.35f +
            consistency * 0.40f +
            recency * 0.25f
        ).coerceIn(0f, 1f)
}

data class AdaptiveLearningContext(
    val preferences: List<LearnedPreference> = emptyList(),
    val adjustments: List<StrategyAdjustment> = emptyList(),
    val recentFeedback: List<DecisionFeedback> = emptyList()
)

data class LearningUpdateResult(
    val feedbackId: String,
    val learnedPreferences: List<LearnedPreference>,
    val strategyAdjustments: List<StrategyAdjustment>,
    val ignoredSignals: List<LearningSignal>,
    val confidence: LearningConfidence
)

data class LearningQuery(
    val key: String? = null,
    val scope: LearningScope? = null,
    val minimumConfidence: LearningConfidence =
        LearningConfidence.MEDIUM
)
