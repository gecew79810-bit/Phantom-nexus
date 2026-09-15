package com.pantham.nexus.intelligence.model

enum class InputChannel {
    VOICE,
    TEXT,
    SYSTEM,
    WAKE_WORD,
    ROUTINE,
    VISION
}

data class ResolvedEntity(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val value: String = ""
)

data class MemoryCandidate(
    val key: String = "",
    val value: String = "",
    val relevance: Double = 1.0,
    val category: String? = null,
    val id: String = key,
    val content: String = value,
    val score: Float = relevance.toFloat(),
    val timestamp: Long = System.currentTimeMillis()
)

data class NexusContext(
    val conversationId: String = java.util.UUID.randomUUID().toString(),
    val taskId: String? = null,
    val inputChannel: InputChannel = InputChannel.TEXT,
    val userInput: String = "",
    val currentTimeMillis: Long = System.currentTimeMillis(),
    val currentPackage: String? = null,
    val currentScreenSummary: String? = null,
    val currentLocation: String? = null,
    val batteryPercent: Int? = null,
    val networkAvailable: Boolean = true,
    val currentMedia: String? = null,
    val relevantMemories: List<MemoryCandidate> = emptyList(),
    val recentEntities: List<ResolvedEntity> = emptyList(),
    val pendingActionId: String? = null,
    val activeRoutineId: String? = null,
    val visualContext: com.pantham.nexus.vision.model.VisualContext? = null,
    val knowledgeContext: com.pantham.nexus.knowledge.KnowledgeContext? = null,
    val predictiveContext: com.pantham.nexus.prediction.PredictiveContext? = null,
    val situationalContext: com.pantham.nexus.situational.model.SituationalContext? = null,
    val fileIntelligenceContext: com.pantham.nexus.files.model.FileIntelligenceContext? = null,
    val decisionContext: com.pantham.nexus.decision.model.DecisionContext? = null,
    val adaptiveLearningContext: com.pantham.nexus.learning.model.AdaptiveLearningContext? = null
)

enum class ConfidenceLevel {
    VERY_LOW,
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class IntentType {
    UNKNOWN,
    OPEN_APP,
    SEND_MESSAGE,
    MAKE_CALL,
    MEDIA_CONTROL,
    RESEARCH,
    FILE_OPERATION,
    CALENDAR,
    REMINDER,
    AUTOMATION,
    SYSTEM_SETTINGS,
    DEVICE_DIAGNOSTICS,
    QUERY_INFO,
    DOCUMENT_GENERATION,
    PRESENTATION_GENERATION,
    WEB_SEARCH,
    READ_NOTIFICATIONS,
    GENERAL_CHAT
}

data class IntentResult(
    val type: IntentType,
    val confidence: Double,
    val confidenceLevel: ConfidenceLevel,
    val missingSlots: List<String> = emptyList(),
    val risk: RiskLevel = RiskLevel.LOW
)

sealed interface TaskCondition {
    object Always : TaskCondition

    data class NodeSucceeded(
        val nodeId: String
    ) : TaskCondition

    data class NodeProducedValue(
        val nodeId: String,
        val key: String,
        val expected: String? = null
    ) : TaskCondition
}

data class TaskNode(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val intent: IntentType,
    val risk: RiskLevel = RiskLevel.LOW,
    val parallelGroup: Int? = null,
    val dependencies: Set<String> = emptySet(),
    val condition: TaskCondition? = null
)

data class TaskPlan(
    val goal: String,
    val nodes: List<TaskNode> = emptyList(),
    val taskId: String = java.util.UUID.randomUUID().toString()
)

data class AgentRequest(
    val taskId: String,
    val node: TaskNode,
    val context: NexusContext
)

data class AgentResult(
    val success: Boolean,
    val message: String = "",
    val output: Map<String, Any?> = emptyMap(),
    val shouldReplan: Boolean = false,
    val failureReason: String? = null
)

enum class TaskStatus {
    PENDING,
    PLANNING,
    RUNNING,
    WAITING_FOR_INPUT,
    WAITING_FOR_CONFIRMATION,
    COMPLETED,
    FAILED,
    CANCELLED,
    DEADLOCKED
}

data class IntelligenceResponse(
    val message: String,
    val taskId: String,
    val taskStatus: TaskStatus,
    val requiresClarification: Boolean = false,
    val clarificationQuestion: String? = null,
    val requiresConfirmation: Boolean = false,
    val confirmationDescription: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class TaskCheckpoint(
    val taskId: String,
    val status: TaskStatus,
    val completedNodeIds: Set<String> = emptySet(),
    val failedNodeIds: Set<String> = emptySet(),
    val pendingNodeIds: Set<String> = emptySet(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ActionStatus {
    EXECUTED,
    WAITING_FOR_PERMISSION,
    WAITING_FOR_CONFIRMATION,
    FAILED
}
