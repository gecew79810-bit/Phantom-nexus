package com.pantham.nexus.task.learning

import android.util.Log
import com.pantham.nexus.learning.integration.NexusLearningOutcomeBridge
import com.pantham.nexus.learning.model.*
import com.pantham.nexus.learning.runtime.NexusAdaptiveLearningRuntime
import com.pantham.nexus.task.model.AutonomousTask
import com.pantham.nexus.task.model.TaskExecutionStatus

/**
 * Feeds verified autonomous execution outcomes back into the Adaptive Learning & Feedback Core.
 * Ensures the system improves future decision-making based on real-world task results.
 */
class TaskExecutionLearningBridge(
    private val outcomeBridge: NexusLearningOutcomeBridge = NexusLearningOutcomeBridge(),
    private val learningRuntime: NexusAdaptiveLearningRuntime = NexusAdaptiveLearningRuntime.getInstance()
) {
    companion object {
        private const val TAG = "TaskLearningBridge"
    }

    suspend fun onTaskFinished(task: AutonomousTask) {
        val outcomeStatus = when (task.status) {
            TaskExecutionStatus.COMPLETED -> OutcomeStatus.SUCCESS
            TaskExecutionStatus.FAILED -> OutcomeStatus.FAILURE
            TaskExecutionStatus.CANCELLED -> OutcomeStatus.CANCELLED
            TaskExecutionStatus.PAUSED, TaskExecutionStatus.BLOCKED, TaskExecutionStatus.WAITING_USER_INPUT -> OutcomeStatus.PARTIAL
            else -> OutcomeStatus.UNKNOWN
        }

        try {
            // 1. Report to Canonical Outcome Bridge
            outcomeBridge.onTaskFinished(
                taskId = task.taskId,
                actionName = "AutonomousGoal_${task.goalDefinition.priority}",
                success = outcomeStatus == OutcomeStatus.SUCCESS,
                failureReason = if (outcomeStatus == OutcomeStatus.FAILURE) task.failedSteps.joinToString() else null
            )

            // 2. Formulate explicit learning signal for future task planning
            val signalType = if (outcomeStatus == OutcomeStatus.SUCCESS) {
                LearningSignalType.OUTCOME_SUCCESS
            } else {
                LearningSignalType.OUTCOME_FAILURE
            }

            val signal = LearningSignal(
                id = "SIG_" + System.currentTimeMillis() + "_" + (1000..9999).random(),
                type = signalType,
                key = "task_execution_strategy",
                value = if (outcomeStatus == OutcomeStatus.SUCCESS) "autonomous_dag_verified" else "execution_failed",
                confidence = if (outcomeStatus == OutcomeStatus.SUCCESS) 0.85f else 0.75f,
                scope = LearningScope.TASK,
                source = "AutonomousTask_${task.taskId}",
                timestamp = System.currentTimeMillis()
            )

            val decisionOutcome = DecisionOutcome(
                decisionId = task.taskId,
                status = outcomeStatus,
                selectedOptionId = "execute_autonomous_dag",
                actualResult = if (outcomeStatus == OutcomeStatus.SUCCESS) "Completed ${task.completedSteps.size} steps" else "Failed",
                userSatisfied = outcomeStatus == OutcomeStatus.SUCCESS,
                observedAt = System.currentTimeMillis()
            )

            learningRuntime.recordSignal(signal, decisionOutcome)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to feed task execution outcome into learning core", e)
        }
    }
}
