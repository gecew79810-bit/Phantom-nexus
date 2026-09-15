package com.pantham.nexus.task.controller

import android.content.Context
import android.util.Log
import com.example.action.NexusActionRouter
import com.example.action.goal.*
import com.example.ai.TaskItem
import com.example.voice.AssistantLanguage
import com.pantham.nexus.task.model.AutonomousTask
import com.pantham.nexus.task.model.StepVerificationRule
import com.pantham.nexus.task.model.TaskExecutionReport
import com.pantham.nexus.task.model.TaskExecutionStatus
import com.pantham.nexus.task.supervisor.NexusTaskSupervisor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * High-level Autonomous Task Controller.
 * Provides the unified external interface for ingesting user goals, planning,
 * executing, observing, verifying, recovering, and resuming autonomous operations.
 */
class AutonomousTaskController(
    private val supervisor: NexusTaskSupervisor,
    private val goalPlanner: AutonomousGoalPlanner,
    private val checkpointStore: TaskCheckpointStore,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val TAG = "AutonomousTaskController"
    }

    val activeTaskState: StateFlow<AutonomousTask?> = supervisor.activeTask

    /**
     * Executes an autonomous user goal through the complete pipeline:
     * Understand -> Plan -> Supervised Execute -> Observe -> Verify -> Learn
     */
    suspend fun executeGoal(
        userGoal: String,
        language: AssistantLanguage = AssistantLanguage.HINDI,
        verificationRules: Map<String, StepVerificationRule> = emptyMap()
    ): TaskExecutionReport = withContext(Dispatchers.Default) {
        Log.i(TAG, "Ingesting user goal: $userGoal")

        // 1. UNDERSTAND & PLAN / DECOMPOSE GOAL
        val (goalDef, nodes) = goalPlanner.planGoal(userGoal)

        // 2. SUPERVISED EXECUTION
        supervisor.superviseTask(
            goal = goalDef,
            nodes = nodes,
            verificationRules = verificationRules,
            resumeFromCheckpoint = false,
            language = language
        )
    }

    /**
     * Resumes the currently active or specified task from its safe checkpoint.
     */
    suspend fun resumeTask(
        taskId: String? = null,
        language: AssistantLanguage = AssistantLanguage.HINDI
    ): TaskExecutionReport? = withContext(Dispatchers.Default) {
        val checkpoint = if (taskId != null) {
            checkpointStore.getCheckpoint(taskId)
        } else {
            checkpointStore.getActiveCheckpoint()
        } ?: return@withContext null

        Log.i(TAG, "Resuming task from checkpoint: ${checkpoint.taskId}")

        // Reconstruct goal and nodes
        val (goalDef, nodes) = goalPlanner.planGoal(checkpoint.goal)

        supervisor.superviseTask(
            goal = goalDef.copy(goalId = checkpoint.taskId),
            nodes = nodes,
            resumeFromCheckpoint = true,
            language = language
        )
    }

    /**
     * Resumes a blocked task after user has provided clarification or input.
     */
    suspend fun provideClarification(
        taskId: String,
        userInput: String,
        language: AssistantLanguage = AssistantLanguage.HINDI
    ): TaskExecutionReport? = withContext(Dispatchers.Default) {
        val checkpoint = checkpointStore.getCheckpoint(taskId) ?: return@withContext null
        Log.i(TAG, "Providing clarification '$userInput' for blocked task: $taskId")

        // Revise plan if necessary
        val (goalDef, originalNodes) = goalPlanner.planGoal(checkpoint.goal)
        val revisedNodes = goalPlanner.reviseActivePlan(userInput, originalNodes)

        supervisor.superviseTask(
            goal = goalDef.copy(goalId = taskId),
            nodes = revisedNodes,
            resumeFromCheckpoint = true,
            language = language
        )
    }

    fun pauseTask(taskId: String): String {
        return supervisor.pauseActiveTask(taskId)
    }

    fun cancelTask(taskId: String): String {
        return supervisor.cancelActiveTask(taskId)
    }
}
