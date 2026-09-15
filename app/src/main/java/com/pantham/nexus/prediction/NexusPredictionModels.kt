package com.pantham.nexus.prediction

import java.util.UUID

enum class PredictionType {
    NEXT_INTENT,
    NEXT_ACTION,
    GOAL_STEP,
    ROUTINE,
    REMINDER,
    PREPARATION,
    MISSING_INFORMATION,
    RISK,
    FOLLOW_UP,
    CONTEXT_CHANGE,
    PROACTIVE_SUGGESTION
}

enum class PredictionConfidence {
    VERY_LOW,
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}

enum class PredictionSource {
    CURRENT_CONTEXT,
    CONVERSATION,
    PERSONAL_KNOWLEDGE,
    MEMORY,
    TEMPORAL_PATTERN,
    ACTION_HISTORY,
    VISION,
    CALENDAR,
    DEVICE_STATE,
    USER_FEEDBACK,
    INFERENCE
}

enum class PredictionDisposition {
    IGNORE,
    INTERNAL_ONLY,
    SUGGEST,
    REQUIRE_CONFIRMATION,
    ACTION_READY
}

enum class PredictionFeedback {
    ACCEPTED,
    REJECTED,
    DISMISSED,
    CORRECTED,
    EXPIRED,
    NOT_SHOWN
}

enum class RiskLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class PredictionEvidence(
    val source: PredictionSource,
    val description: String,
    val weight: Float = 1f,
    val timestamp: Long = System.currentTimeMillis()
)

data class NexusPrediction(
    val id: String = UUID.randomUUID().toString(),
    val type: PredictionType,
    val title: String,
    val description: String,

    val predictedIntent: String? = null,
    val predictedAction: String? = null,

    val targetEntityId: String? = null,
    val relatedGoalId: String? = null,

    val confidence: PredictionConfidence,
    val confidenceScore: Float,

    val disposition: PredictionDisposition,

    val evidence: List<PredictionEvidence> = emptyList(),

    val requiredSlots: Set<String> = emptySet(),
    val missingSlots: Set<String> = emptySet(),

    val riskLevel: RiskLevel = RiskLevel.NONE,

    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,

    val metadata: Map<String, String> = emptyMap()
)

data class PredictionContext(
    val userText: String,

    val currentIntent: String? = null,
    val activeGoalId: String? = null,

    val resolvedEntityIds: List<String> = emptyList(),

    val recentIntents: List<String> = emptyList(),
    val recentActions: List<String> = emptyList(),

    val currentTimeMillis: Long =
        System.currentTimeMillis(),

    val foregroundPackage: String? = null,

    val deviceState: Map<String, String> =
        emptyMap(),

    val memorySignals: List<String> =
        emptyList(),

    val knowledgeSignals: List<String> =
        emptyList(),

    val temporalSignals: List<String> =
        emptyList(),

    val visionSignals: List<String> =
        emptyList(),

    val calendarSignals: List<String> =
        emptyList()
)

data class PredictionBatch(
    val predictions: List<NexusPrediction>,
    val generatedAt: Long = System.currentTimeMillis()
)

data class PredictionFeedbackRecord(
    val predictionId: String,
    val feedback: PredictionFeedback,
    val correction: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class PredictionPolicyDecision(
    val prediction: NexusPrediction,
    val disposition: PredictionDisposition,
    val reason: String
)
