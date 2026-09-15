package com.pantham.nexus.task.supervisor

import android.util.Log
import com.example.action.NexusActionRouter
import com.example.action.goal.*
import com.example.ai.ActionStatus
import com.example.ai.TaskItem
import com.example.voice.AssistantLanguage
import com.pantham.nexus.task.learning.TaskExecutionLearningBridge
import com.pantham.nexus.task.model.*
import com.pantham.nexus.task.observation.ExecutionEnvironmentObserver
import com.pantham.nexus.task.recovery.TaskRecoveryEngine
import com.pantham.nexus.task.verification.RealWorldExecutionVerifier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Intelligent Supervisor Layer for Autonomous Task Execution.
 * Supervises existing TaskDAGExecutor, CheckpointStore, GoalPlanner, and ActionRouter
 * without duplicating execution primitives.
 *
 * Enforces the complete lifecycle:
 * UNDERSTAND -> PLAN -> DAG -> EXECUTE -> OBSERVE -> VERIFY -> RECOVER/REPLAN -> LEARN
 */
class NexusTaskSupervisor(
    private val actionRouter: NexusActionRouter? = null,
    private val checkpointStore: TaskCheckpointStore? = null,
    private val artifactRegistry: ArtifactRegistry? = null,
    private val onTaskUpdated: (TaskItem) -> Unit = {},
    private val verifier: RealWorldExecutionVerifier = RealWorldExecutionVerifier(),
    private val observer: ExecutionEnvironmentObserver = ExecutionEnvironmentObserver(),
    private val recoveryEngine: TaskRecoveryEngine = TaskRecoveryEngine(),
    private val learningBridge: TaskExecutionLearningBridge = TaskExecutionLearningBridge(),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val TAG = "NexusTaskSupervisor"
    }

    private val isInterrupted = AtomicBoolean(false)
    private val isCancellationRequested = AtomicBoolean(false)

    private val _activeTask = MutableStateFlow<AutonomousTask?>(null)
    val activeTask: StateFlow<AutonomousTask?> = _activeTask.asStateFlow()

    fun pauseActiveTask(taskId: String): String {
        isInterrupted.set(true)
        checkpointStore?.markPaused(taskId, true)
        val current = _activeTask.value
        if (current != null && current.taskId == taskId) {
            _activeTask.value = current.copy(
                status = TaskExecutionStatus.PAUSED,
                updatedAt = System.currentTimeMillis()
            )
        }
        return "Autonomous task '$taskId' paused safely. Durable checkpoint stored."
    }

    fun cancelActiveTask(taskId: String): String {
        isCancellationRequested.set(true)
        isInterrupted.set(true)
        checkpointStore?.markCancelled(taskId)
        val current = _activeTask.value
        if (current != null && current.taskId == taskId) {
            _activeTask.value = current.copy(
                status = TaskExecutionStatus.CANCELLED,
                updatedAt = System.currentTimeMillis()
            )
        }
        return "Autonomous task '$taskId' cancelled."
    }

    /**
     * Executes an autonomous task under full intelligent supervision.
     */
    suspend fun superviseTask(
        goal: GoalDefinition,
        nodes: List<TaskNode>,
        verificationRules: Map<String, StepVerificationRule> = emptyMap(),
        resumeFromCheckpoint: Boolean = false,
        language: AssistantLanguage = AssistantLanguage.HINDI
    ): TaskExecutionReport {
        isInterrupted.set(false)
        isCancellationRequested.set(false)

        val taskId = goal.goalId
        val conversationId = goal.conversationId
        val completedSteps = mutableListOf<String>()
        val stepOutputs = mutableMapOf<String, Any?>()
        val allArtifacts = mutableListOf<TaskArtifact>()
        val timeline = mutableListOf<TaskTimelineEvent>()
        val retryCounts = mutableMapOf<String, Int>()
        var retriesCountTotal = 0
        var replansCountTotal = 0

        timeline.add(
            TaskTimelineEvent(
                stage = "INITIALIZE",
                description = "Task supervisor initialized for goal: ${goal.userGoal}"
            )
        )

        // 1. Restore from checkpoint if requested
        var workingNodes = nodes.toMutableList()
        if (resumeFromCheckpoint && checkpointStore != null) {
            val checkpoint = checkpointStore.getCheckpoint(taskId) ?: checkpointStore.getActiveCheckpoint()
            if (checkpoint != null && checkpoint.safeResumeState) {
                completedSteps.addAll(checkpoint.completedSteps)
                checkpoint.stepOutputs.forEach { (k, v) -> stepOutputs[k] = v }
                workingNodes = nodes.filter { it.nodeId !in checkpoint.completedSteps }.toMutableList()
                timeline.add(
                    TaskTimelineEvent(
                        stage = "RESUME",
                        description = "Resumed from checkpoint: ${checkpoint.completedSteps.size} steps previously completed."
                    )
                )
            }
        }

        // 2. Initial Situational Snapshot
        val initialSnapshot = observer.captureSnapshot()
        timeline.add(
            TaskTimelineEvent(
                stage = "OBSERVE_INITIAL",
                description = "Initial environment: Situation=${initialSnapshot.situationState}, Wifi=${initialSnapshot.isWifi}, Battery=${initialSnapshot.batteryPercent}%"
            )
        )

        // Setup Task State
        var currentTask = AutonomousTask(
            taskId = taskId,
            userGoal = goal.userGoal,
            conversationId = conversationId,
            status = TaskExecutionStatus.EXECUTING,
            goalDefinition = goal,
            nodes = nodes,
            currentStepIndex = completedSteps.size,
            completedSteps = completedSteps,
            stepOutputs = stepOutputs,
            artifacts = allArtifacts,
            timeline = timeline
        )
        _activeTask.value = currentTask

        val dependenciesMap = nodes.associate { it.nodeId to it.dependencies.toList() }
        val remainingNodeMap = workingNodes.associateBy { it.nodeId }.toMutableMap()

        // 3. Supervised Execution Loop
        while (remainingNodeMap.isNotEmpty()) {
            if (isCancellationRequested.get()) {
                currentTask = currentTask.copy(
                    status = TaskExecutionStatus.CANCELLED,
                    updatedAt = System.currentTimeMillis()
                )
                _activeTask.value = currentTask
                learningBridge.onTaskFinished(currentTask)
                return buildReport(currentTask, retriesCountTotal, replansCountTotal)
            }

            if (isInterrupted.get()) {
                currentTask = currentTask.copy(
                    status = TaskExecutionStatus.PAUSED,
                    updatedAt = System.currentTimeMillis()
                )
                _activeTask.value = currentTask
                saveCheckpoint(currentTask, dependenciesMap)
                learningBridge.onTaskFinished(currentTask)
                return buildReport(currentTask, retriesCountTotal, replansCountTotal)
            }

            // Find executable nodes
            val readyNodes = remainingNodeMap.values.filter { node ->
                node.dependencies.all { depId -> completedSteps.contains(depId) }
            }

            if (readyNodes.isEmpty()) {
                val deadlockMsg = "Dependency resolution deadlock: Unmet dependencies for remaining steps."
                Log.e(TAG, deadlockMsg)
                timeline.add(TaskTimelineEvent(stage = "DEADLOCK", description = deadlockMsg))
                currentTask = currentTask.copy(
                    status = TaskExecutionStatus.FAILED,
                    failedSteps = remainingNodeMap.keys.toList(),
                    updatedAt = System.currentTimeMillis()
                )
                _activeTask.value = currentTask
                learningBridge.onTaskFinished(currentTask)
                return buildReport(currentTask, retriesCountTotal, replansCountTotal)
            }

            // 4. Environmental Observation before wave
            val currentSnapshot = observer.captureSnapshot()
            val contextEval = observer.evaluateContextChange(
                initialSnapshot = initialSnapshot,
                currentSnapshot = currentSnapshot,
                stepRequiresNetwork = readyNodes.any { it.title.contains("Weather", true) || it.title.contains("Research", true) }
            )

            if (contextEval.requiresPauseOrReplan) {
                timeline.add(
                    TaskTimelineEvent(
                        stage = "CONTEXT_CHANGED",
                        description = "Execution paused due to context change: ${contextEval.reason}"
                    )
                )
                currentTask = currentTask.copy(
                    status = TaskExecutionStatus.BLOCKED,
                    blockedReason = BlockedReason("CONTEXT_CHANGED", contextEval.reason, contextEval.reason),
                    updatedAt = System.currentTimeMillis()
                )
                _activeTask.value = currentTask
                saveCheckpoint(currentTask, dependenciesMap)
                learningBridge.onTaskFinished(currentTask)
                return buildReport(currentTask, retriesCountTotal, replansCountTotal)
            }

            // Group parallel or sequential
            val parallelNodes = readyNodes.filter { it.isParallel }
            val batch = if (parallelNodes.size > 1) parallelNodes else listOf(readyNodes.first())

            // Execute Batch
            var batchBlocked: BlockedReason? = null

            for (node in batch) {
                var stepCompleted = false
                var nodeRetries = retryCounts[node.nodeId] ?: 0

                while (!stepCompleted) {
                    // Update TaskItem UI
                    val taskItem = TaskItem(
                        id = node.nodeId,
                        time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                        title = node.title,
                        isCompleted = false,
                        progress = 40,
                        agent = node.agent,
                        status = ActionStatus.RUNNING,
                        details = "Supervised execution of: ${node.title}"
                    )
                    onTaskUpdated(taskItem)

                    // Execute Step
                    val stepResult = executeNode(node, stepOutputs, language)

                    // Real-World Verification
                    val rule = verificationRules[node.nodeId]
                    val verification = verifier.verifyStep(node, stepResult, rule)

                    if (stepResult.success && verification.verified) {
                        // Success verified!
                        stepCompleted = true
                        completedSteps.add(node.nodeId)
                        remainingNodeMap.remove(node.nodeId)

                        stepOutputs[node.nodeId] = stepResult.outputData[node.nodeId] ?: stepResult.message
                        stepResult.outputData.forEach { (k, v) -> stepOutputs[k] = v }
                        stepResult.artifacts.forEach { art ->
                            artifactRegistry?.registerArtifact(art)
                            allArtifacts.add(art)
                        }

                        timeline.add(
                            TaskTimelineEvent(
                                stage = "STEP_VERIFIED",
                                stepId = node.nodeId,
                                description = "Step '${node.title}' executed and verified: ${verification.reason}"
                            )
                        )

                        onTaskUpdated(
                            taskItem.copy(
                                isCompleted = true,
                                progress = 100,
                                status = ActionStatus.SUCCESS,
                                details = stepResult.message
                            )
                        )
                    } else if (stepResult.message.contains("Skipped due to condition false")) {
                        // Condition false skip
                        stepCompleted = true
                        completedSteps.add(node.nodeId)
                        remainingNodeMap.remove(node.nodeId)
                        timeline.add(
                            TaskTimelineEvent(
                                stage = "STEP_SKIPPED",
                                stepId = node.nodeId,
                                description = "Step '${node.title}' skipped as condition was not met."
                            )
                        )
                    } else {
                        // Failure or Verification Failed -> Recovery Engine
                        nodeRetries++
                        retryCounts[node.nodeId] = nodeRetries
                        retriesCountTotal++

                        val decision = recoveryEngine.handleStepFailure(
                            node = node,
                            stepResult = stepResult,
                            verificationResult = verification,
                            currentRetryCount = nodeRetries
                        )

                        timeline.add(
                            TaskTimelineEvent(
                                stage = "RECOVERY_DECISION",
                                stepId = node.nodeId,
                                description = "Recovery triggered (${decision.strategy}): ${decision.reason}"
                            )
                        )

                        when (decision.strategy) {
                            RecoveryStrategy.RETRY_IMMEDIATE, RecoveryStrategy.RETRY_WITH_BACKOFF -> {
                                if (decision.delayMs > 0) delay(decision.delayMs)
                                // Will retry in loop
                            }
                            RecoveryStrategy.FALLBACK_ALTERNATIVE_STEP -> {
                                if (decision.alternativeNode != null) {
                                    replansCountTotal++
                                    remainingNodeMap[node.nodeId] = decision.alternativeNode
                                    timeline.add(
                                        TaskTimelineEvent(
                                            stage = "REPLAN_ALTERNATIVE",
                                            stepId = node.nodeId,
                                            description = "Swapped to alternative node: ${decision.alternativeNode.title}"
                                        )
                                    )
                                    break // Break retry loop to execute alternative
                                } else {
                                    batchBlocked = recoveryEngine.buildBlockedReason(node, decision, stepResult)
                                    stepCompleted = true
                                }
                            }
                            RecoveryStrategy.ASK_USER -> {
                                batchBlocked = recoveryEngine.buildBlockedReason(node, decision, stepResult)
                                stepCompleted = true
                            }
                            RecoveryStrategy.REPLAN_SUBGRAPH -> {
                                replansCountTotal++
                                // Mark skipped and continue
                                stepCompleted = true
                                remainingNodeMap.remove(node.nodeId)
                            }
                            RecoveryStrategy.FAIL_GRACEFULLY -> {
                                batchBlocked = recoveryEngine.buildBlockedReason(node, decision, stepResult)
                                stepCompleted = true
                            }
                        }
                    }
                }

                if (batchBlocked != null) break
            }

            // Handle Blocked State
            if (batchBlocked != null) {
                currentTask = currentTask.copy(
                    status = TaskExecutionStatus.WAITING_USER_INPUT,
                    blockedReason = batchBlocked,
                    completedSteps = completedSteps.toList(),
                    stepOutputs = stepOutputs.toMap(),
                    artifacts = allArtifacts.toList(),
                    timeline = timeline.toList(),
                    updatedAt = System.currentTimeMillis()
                )
                _activeTask.value = currentTask
                saveCheckpoint(currentTask, dependenciesMap)
                learningBridge.onTaskFinished(currentTask)
                return buildReport(currentTask, retriesCountTotal, replansCountTotal)
            }

            // Save durable checkpoint after each wave
            currentTask = currentTask.copy(
                currentStepIndex = completedSteps.size,
                completedSteps = completedSteps.toList(),
                stepOutputs = stepOutputs.toMap(),
                artifacts = allArtifacts.toList(),
                timeline = timeline.toList(),
                updatedAt = System.currentTimeMillis()
            )
            _activeTask.value = currentTask
            saveCheckpoint(currentTask, dependenciesMap)
        }

        // All steps completed!
        checkpointStore?.clearCheckpoint(taskId)
        currentTask = currentTask.copy(
            status = TaskExecutionStatus.COMPLETED,
            updatedAt = System.currentTimeMillis()
        )
        _activeTask.value = currentTask
        learningBridge.onTaskFinished(currentTask)

        timeline.add(
            TaskTimelineEvent(
                stage = "COMPLETED",
                description = "Autonomous task fully completed. ${completedSteps.size} steps verified."
            )
        )

        return buildReport(currentTask, retriesCountTotal, replansCountTotal)
    }

    private suspend fun executeNode(
        node: TaskNode,
        contextOutputs: Map<String, Any?>,
        language: AssistantLanguage
    ): TaskStepResult {
        // 1. Condition Check
        if (node.condition != null) {
            val met = try {
                node.condition.evaluate(contextOutputs)
            } catch (e: Exception) {
                false
            }
            if (!met) {
                return TaskStepResult(
                    nodeId = node.nodeId,
                    success = false,
                    message = "Skipped due to condition false"
                )
            }
        }

        // 2. Custom execution or Action Router
        return if (node.customExecution != null) {
            try {
                node.customExecution.invoke(contextOutputs)
            } catch (e: Exception) {
                TaskStepResult(node.nodeId, false, e.message ?: "Execution exception")
            }
        } else if (node.action != null && actionRouter != null) {
            val result = actionRouter.routeAction(node.action, bypassConfirmation = true, language = language)
            TaskStepResult(
                nodeId = node.nodeId,
                success = result.success,
                message = result.message,
                outputData = mapOf(node.nodeId to result.message)
            )
        } else {
            TaskStepResult(node.nodeId, true, "Completed empty step")
        }
    }

    private fun saveCheckpoint(
        task: AutonomousTask,
        dependenciesMap: Map<String, List<String>>
    ) {
        val remaining = task.nodes.filter { it.nodeId !in task.completedSteps }.map { it.nodeId }
        val stringOutputs = task.stepOutputs.mapValues { it.value?.toString() ?: "" }
        val checkpoint = TaskCheckpoint(
            taskId = task.taskId,
            conversationId = task.conversationId,
            goal = task.userGoal,
            completedSteps = task.completedSteps,
            remainingSteps = remaining,
            currentStep = remaining.firstOrNull(),
            stepOutputs = stringOutputs,
            dependencies = dependenciesMap,
            createdAt = task.createdAt,
            updatedAt = System.currentTimeMillis(),
            safeResumeState = true,
            isPaused = task.status == TaskExecutionStatus.PAUSED,
            isCancelled = task.status == TaskExecutionStatus.CANCELLED
        )
        checkpointStore?.saveCheckpoint(checkpoint)
    }

    private fun buildReport(
        task: AutonomousTask,
        retries: Int,
        replans: Int
    ): TaskExecutionReport {
        val summary = when (task.status) {
            TaskExecutionStatus.COMPLETED ->
                "Autonomous Goal '${task.userGoal}' completed successfully. ${task.completedSteps.size} of ${task.nodes.size} steps verified."
            TaskExecutionStatus.WAITING_USER_INPUT, TaskExecutionStatus.BLOCKED ->
                "Task paused: ${task.blockedReason?.clarificationPrompt ?: "Awaiting user input."}"
            TaskExecutionStatus.PAUSED ->
                "Task safely paused at checkpoint with ${task.completedSteps.size} steps completed."
            TaskExecutionStatus.CANCELLED ->
                "Task cancelled by user."
            else ->
                "Task finished with status ${task.status}. ${task.completedSteps.size} steps completed."
        }

        return TaskExecutionReport(
            taskId = task.taskId,
            userGoal = task.userGoal,
            status = task.status,
            totalSteps = task.nodes.size,
            completedStepsCount = task.completedSteps.size,
            retriesCount = retries,
            replansCount = replans,
            artifacts = task.artifacts,
            summary = summary,
            durationMs = task.updatedAt - task.createdAt,
            timeline = task.timeline
        )
    }
}
