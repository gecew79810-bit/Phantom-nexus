package com.pantham.nexus.task.model

import com.example.action.goal.GoalDefinition
import com.example.action.goal.TaskArtifact
import com.example.action.goal.TaskNode
import com.pantham.nexus.situational.model.SituationState

/**
 * High-level lifecycle states of an Autonomous Task.
 */
enum class TaskExecutionStatus {
    IDLE,
    UNDERSTANDING,
    PLANNING,
    READY,
    EXECUTING,
    OBSERVING,
    VERIFYING,
    RETRYING,
    REPLANNING,
    BLOCKED,
    WAITING_USER_INPUT,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Status of an individual task node during execution.
 */
enum class StepExecutionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    PARTIAL,
    FAILED,
    VERIFICATION_FAILED,
    RETRYING,
    BLOCKED,
    SKIPPED
}

/**
 * High-level task outcome categories matching the supervisor workflow.
 */
enum class TaskOutcomeType {
    SUCCESS,
    FAILURE,
    PARTIAL,
    BLOCKED,
    CONTEXT_CHANGED
}

/**
 * Types of real-world verifications supported.
 */
enum class VerificationType {
    OUTPUT_NON_EMPTY,
    ARTIFACT_EXISTS,
    FILE_CONTENT_VALID,
    SYSTEM_STATE_CHANGED,
    CUSTOM_CRITERIA
}

/**
 * Rule definition for verifying real-world post-conditions of a step.
 */
data class StepVerificationRule(
    val ruleId: String,
    val type: VerificationType,
    val description: String,
    val targetKey: String? = null,
    val expectedArtifactType: String? = null,
    val customValidator: (suspend (outputs: Map<String, Any?>, artifacts: List<TaskArtifact>) -> Boolean)? = null
)

/**
 * Detailed result of real-world verification.
 */
data class VerificationResult(
    val verified: Boolean,
    val reason: String,
    val type: VerificationType = VerificationType.OUTPUT_NON_EMPTY,
    val verifiedAt: Long = System.currentTimeMillis(),
    val confidence: Float = 1.0f
)

/**
 * Recovery strategies available to the Recovery Engine when a failure or blockage occurs.
 */
enum class RecoveryStrategy {
    RETRY_IMMEDIATE,
    RETRY_WITH_BACKOFF,
    FALLBACK_ALTERNATIVE_STEP,
    REPLAN_SUBGRAPH,
    ASK_USER,
    FAIL_GRACEFULLY
}

/**
 * A recovery decision produced by the Recovery Engine (optionally guided by Decision Intelligence).
 */
data class RecoveryDecision(
    val strategy: RecoveryStrategy,
    val reason: String,
    val alternativeNode: TaskNode? = null,
    val delayMs: Long = 0L,
    val clarificationPrompt: String? = null
)

/**
 * Information describing why a task is currently blocked and what is required from the user.
 */
data class BlockedReason(
    val code: String,
    val description: String,
    val clarificationPrompt: String,
    val requiredInputType: String = "TEXT",
    val candidateOptions: List<String> = emptyList()
)

/**
 * Snapshot of environmental and situational conditions observed before or during a task.
 */
data class TaskObservationSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val situationState: SituationState = SituationState.UNKNOWN,
    val networkAvailable: Boolean = true,
    val isWifi: Boolean = true,
    val batteryPercent: Int = 100,
    val isLowBattery: Boolean = false,
    val isUserDriving: Boolean = false,
    val isAudioBusy: Boolean = false,
    val foregroundApp: String? = null
)

/**
 * Evaluation of context change impact on an in-progress task.
 */
enum class ContextChangeSeverity {
    NONE,
    LOW,
    MEDIUM,
    CRITICAL_REPLAN_NEEDED
}

data class ContextEvaluation(
    val severity: ContextChangeSeverity,
    val reason: String,
    val requiresPauseOrReplan: Boolean = false
)

/**
 * Structured timeline audit event for explainable execution.
 */
data class TaskTimelineEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val stage: String,
    val stepId: String? = null,
    val description: String,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Encapsulates the complete state of an Autonomous Task managed by the supervisor.
 */
data class AutonomousTask(
    val taskId: String,
    val userGoal: String,
    val conversationId: String,
    val status: TaskExecutionStatus = TaskExecutionStatus.IDLE,
    val goalDefinition: GoalDefinition,
    val nodes: List<TaskNode>,
    val currentStepIndex: Int = 0,
    val completedSteps: List<String> = emptyList(),
    val failedSteps: List<String> = emptyList(),
    val stepOutputs: Map<String, Any?> = emptyMap(),
    val artifacts: List<TaskArtifact> = emptyList(),
    val timeline: List<TaskTimelineEvent> = emptyList(),
    val retryCounts: Map<String, Int> = emptyMap(),
    val blockedReason: BlockedReason? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Comprehensive, user-facing and machine-readable execution report.
 */
data class TaskExecutionReport(
    val taskId: String,
    val userGoal: String,
    val status: TaskExecutionStatus,
    val totalSteps: Int,
    val completedStepsCount: Int,
    val retriesCount: Int,
    val replansCount: Int,
    val artifacts: List<TaskArtifact>,
    val summary: String,
    val durationMs: Long,
    val timeline: List<TaskTimelineEvent>
)
