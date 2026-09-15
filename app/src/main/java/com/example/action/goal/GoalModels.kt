package com.example.action.goal

import com.example.action.NexusAction
import com.example.ai.AgentType

enum class GoalPriority {
    CRITICAL,
    HIGH,
    NORMAL,
    LOW
}

enum class ArtifactType {
    FILE,
    PDF,
    SPREADSHEET,
    PRESENTATION,
    REPORT,
    IMAGE,
    AUDIO
}

data class TaskArtifact(
    val artifactId: String = "ART_" + System.currentTimeMillis() + "_" + (1000..9999).random(),
    val name: String,
    val type: ArtifactType,
    val uri: String,
    val description: String,
    val taskId: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class GoalDefinition(
    val goalId: String = "GOAL_" + System.currentTimeMillis() + "_" + (1000..9999).random(),
    val conversationId: String = "CONV_" + System.currentTimeMillis(),
    val userGoal: String,
    val priority: GoalPriority = GoalPriority.NORMAL,
    val createdAt: Long = System.currentTimeMillis()
)

data class TaskStepResult(
    val nodeId: String,
    val success: Boolean,
    val message: String,
    val outputData: Map<String, Any?> = emptyMap(),
    val artifacts: List<TaskArtifact> = emptyList(),
    val requiresUserClarification: Boolean = false,
    val clarificationPrompt: String? = null
)

data class ConditionNode(
    val conditionId: String,
    val description: String,
    val evaluate: suspend (context: Map<String, Any?>) -> Boolean
)

data class TaskNode(
    val nodeId: String,
    val title: String,
    val agent: AgentType,
    val action: NexusAction? = null,
    val dependencies: Set<String> = emptySet(),
    val isParallel: Boolean = false,
    val isReversible: Boolean = true,
    val condition: ConditionNode? = null,
    val customExecution: (suspend (context: Map<String, Any?>) -> TaskStepResult)? = null
)

data class TaskCheckpoint(
    val taskId: String,
    val conversationId: String,
    val goal: String,
    val completedSteps: List<String>,
    val remainingSteps: List<String>,
    val currentStep: String?,
    val stepOutputs: Map<String, String>,
    val dependencies: Map<String, List<String>>,
    val createdAt: Long,
    val updatedAt: Long,
    val safeResumeState: Boolean,
    val isPaused: Boolean = false,
    val isCancelled: Boolean = false
)

data class ExecutionPreflightResult(
    val isReady: Boolean,
    val missingPermissions: List<String> = emptyList(),
    val missingServices: List<String> = emptyList(),
    val unresolvedEntities: List<String> = emptyList(),
    val reason: String? = null
)

data class AmbiguityCandidate(
    val id: String,
    val displayName: String,
    val contextTags: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)

data class AmbiguityResolutionResult(
    val resolved: Boolean,
    val selectedCandidate: AmbiguityCandidate? = null,
    val promptIfUnresolved: String? = null,
    val confidence: Float = 1.0f
)
