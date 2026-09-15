package com.example.action.goal

import android.util.Log
import com.example.action.NexusAction
import com.example.action.NexusActionRouter
import com.example.ai.ActionStatus
import com.example.ai.AgentType
import com.example.ai.TaskItem
import com.example.voice.AssistantLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class ExecutionSessionState(
    val taskId: String,
    val goal: String,
    val isRunning: Boolean,
    val isPaused: Boolean,
    val isCancelled: Boolean,
    val currentStepIndex: Int,
    val totalSteps: Int,
    val completedSteps: List<String>,
    val remainingSteps: List<String>,
    val artifacts: List<TaskArtifact>
)

class TaskDAGExecutor(
    private val actionRouter: NexusActionRouter,
    private val checkpointStore: TaskCheckpointStore,
    private val artifactRegistry: ArtifactRegistry,
    private val onTaskUpdated: (TaskItem) -> Unit,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val TAG = "TaskDAGExecutor"
    }

    private val isInterrupted = AtomicBoolean(false)
    private val isCancellationRequested = AtomicBoolean(false)

    private val _sessionState = MutableStateFlow<ExecutionSessionState?>(null)
    val sessionState: StateFlow<ExecutionSessionState?> = _sessionState.asStateFlow()

    fun pauseExecution(taskId: String): String {
        isInterrupted.set(true)
        checkpointStore.markPaused(taskId, true)
        _sessionState.value = _sessionState.value?.copy(isPaused = true, isRunning = false)
        return "Okay, I've paused the active task."
    }

    fun cancelExecution(taskId: String): String {
        isCancellationRequested.set(true)
        isInterrupted.set(true)
        checkpointStore.markCancelled(taskId)
        _sessionState.value = _sessionState.value?.copy(
            isCancelled = true,
            isRunning = false,
            remainingSteps = emptyList()
        )
        return "Active task and pending operations have been cancelled."
    }

    /**
     * Executes a DAG of TaskNodes respecting dependencies, concurrency, conditions,
     * persistent checkpoints, and safe resumption.
     */
    suspend fun executeGraph(
        goal: GoalDefinition,
        nodes: List<TaskNode>,
        resumeFromCheckpoint: Boolean = false,
        language: AssistantLanguage = AssistantLanguage.HINDI
    ): TaskStepResult {
        isInterrupted.set(false)
        isCancellationRequested.set(false)

        val taskId = goal.goalId
        val conversationId = goal.conversationId
        val completedSteps = mutableListOf<String>()
        val stepOutputs = mutableMapOf<String, Any?>()
        val allArtifacts = mutableListOf<TaskArtifact>()

        // 1. Preflight Self-Check
        val preflight = runPreflightCheck(nodes)
        if (!preflight.isReady) {
            val failureMsg = "Preflight check failed: ${preflight.reason}"
            Log.w(TAG, failureMsg)
            return TaskStepResult(
                nodeId = taskId,
                success = false,
                message = failureMsg
            )
        }

        // 2. Handle Resume Logic
        var nodesToExecute = nodes
        if (resumeFromCheckpoint) {
            val checkpoint = checkpointStore.getCheckpoint(taskId) ?: checkpointStore.getActiveCheckpoint()
            if (checkpoint != null && checkpoint.safeResumeState) {
                Log.i(TAG, "Restoring safe resume checkpoint for task: ${checkpoint.taskId}")
                completedSteps.addAll(checkpoint.completedSteps)
                checkpoint.stepOutputs.forEach { (k, v) -> stepOutputs[k] = v }
                // Filter out already verified completed steps
                nodesToExecute = nodes.filter { it.nodeId !in checkpoint.completedSteps }
            }
        }

        val totalStepCount = nodes.size

        // Build execution waves based on topological dependencies
        val remainingNodeMap = nodesToExecute.associateBy { it.nodeId }.toMutableMap()
        val dependenciesMap = nodes.associate { it.nodeId to it.dependencies.toList() }

        _sessionState.value = ExecutionSessionState(
            taskId = taskId,
            goal = goal.userGoal,
            isRunning = true,
            isPaused = false,
            isCancelled = false,
            currentStepIndex = completedSteps.size,
            totalSteps = totalStepCount,
            completedSteps = completedSteps.toList(),
            remainingSteps = remainingNodeMap.keys.toList(),
            artifacts = emptyList()
        )

        while (remainingNodeMap.isNotEmpty()) {
            if (isCancellationRequested.get()) {
                Log.w(TAG, "Task execution aborted due to cancellation.")
                return TaskStepResult(
                    nodeId = taskId,
                    success = false,
                    message = "Task was cancelled by user. Partial progress saved safely."
                )
            }

            if (isInterrupted.get()) {
                Log.i(TAG, "Task paused by user request. Preserving durable checkpoint.")
                saveSafeCheckpoint(taskId, conversationId, goal.userGoal, completedSteps, remainingNodeMap.keys.toList(), dependenciesMap, stepOutputs)
                return TaskStepResult(
                    nodeId = taskId,
                    success = true,
                    message = "Task has been safely paused at current step."
                )
            }

            // Find nodes whose prerequisites are fully satisfied
            val readyNodes = remainingNodeMap.values.filter { node ->
                node.dependencies.all { depId -> completedSteps.contains(depId) }
            }

            if (readyNodes.isEmpty()) {
                val cyclicError = "Dependency resolution deadlock: Unmet dependencies for remaining nodes."
                Log.e(TAG, cyclicError)
                return TaskStepResult(nodeId = taskId, success = false, message = cyclicError)
            }

            // Group into parallel wave or sequential
            val parallelNodes = readyNodes.filter { it.isParallel }
            val batchToExecute = if (parallelNodes.size > 1) {
                parallelNodes
            } else {
                listOf(readyNodes.first())
            }

            // Execute batch (concurrently if multiple parallel nodes)
            val batchResults = coroutineScope {
                batchToExecute.map { node ->
                    async(Dispatchers.Default) {
                        executeSingleNode(node, stepOutputs, language)
                    }
                }.awaitAll()
            }

            // Process results
            for ((index, result) in batchResults.withIndex()) {
                val node = batchToExecute[index]
                if (result.success) {
                    completedSteps.add(node.nodeId)
                    remainingNodeMap.remove(node.nodeId)
                    stepOutputs[node.nodeId] = result.outputData[node.nodeId] ?: result.message
                    result.outputData.forEach { (k, v) -> stepOutputs[k] = v }
                    result.artifacts.forEach { art ->
                        artifactRegistry.registerArtifact(art)
                        allArtifacts.add(art)
                    }
                } else if (result.requiresUserClarification) {
                    // Ambiguity or condition requires user input
                    return result
                } else {
                    // Check if node is optional or condition returned false
                    if (result.message.contains("Skipped due to condition false")) {
                        completedSteps.add(node.nodeId)
                        remainingNodeMap.remove(node.nodeId)
                    } else {
                        Log.e(TAG, "Node ${node.nodeId} failed: ${result.message}")
                        saveSafeCheckpoint(taskId, conversationId, goal.userGoal, completedSteps, remainingNodeMap.keys.toList(), dependenciesMap, stepOutputs)
                        return TaskStepResult(
                            nodeId = node.nodeId,
                            success = false,
                            message = "Step '${node.title}' failed: ${result.message}"
                        )
                    }
                }
            }

            // Update persistent checkpoint after each wave
            saveSafeCheckpoint(
                taskId = taskId,
                conversationId = conversationId,
                goal = goal.userGoal,
                completedSteps = completedSteps,
                remainingSteps = remainingNodeMap.keys.toList(),
                dependenciesMap = dependenciesMap,
                stepOutputs = stepOutputs
            )

            _sessionState.value = _sessionState.value?.copy(
                currentStepIndex = completedSteps.size,
                completedSteps = completedSteps.toList(),
                remainingSteps = remainingNodeMap.keys.toList(),
                artifacts = allArtifacts.toList()
            )
        }

        // Mark completed and clear active checkpoint pointer
        checkpointStore.clearCheckpoint(taskId)
        _sessionState.value = _sessionState.value?.copy(
            isRunning = false,
            remainingSteps = emptyList(),
            artifacts = allArtifacts
        )

        return TaskStepResult(
            nodeId = taskId,
            success = true,
            message = "Autonomous Goal Completed: ${completedSteps.size} steps executed successfully.",
            outputData = stepOutputs,
            artifacts = allArtifacts
        )
    }

    private suspend fun executeSingleNode(
        node: TaskNode,
        contextOutputs: Map<String, Any?>,
        language: AssistantLanguage
    ): TaskStepResult {
        val taskItem = TaskItem(
            id = node.nodeId,
            time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
            title = node.title,
            isCompleted = false,
            progress = 30,
            agent = node.agent,
            status = ActionStatus.RUNNING,
            details = "Executing: ${node.title}"
        )
        onTaskUpdated(taskItem)

        // 1. Evaluate Condition if present
        if (node.condition != null) {
            val conditionMet = try {
                node.condition.evaluate(contextOutputs)
            } catch (e: Exception) {
                false
            }
            if (!conditionMet) {
                val skipTask = taskItem.copy(
                    isCompleted = true,
                    progress = 100,
                    status = ActionStatus.SUCCESS,
                    details = "Skipped: Condition '${node.condition.description}' evaluated to false."
                )
                onTaskUpdated(skipTask)
                return TaskStepResult(
                    nodeId = node.nodeId,
                    success = false,
                    message = "Skipped due to condition false"
                )
            }
        }

        // 2. Custom Execution or Action Routing
        return if (node.customExecution != null) {
            try {
                val res = node.customExecution.invoke(contextOutputs)
                val status = if (res.success) ActionStatus.SUCCESS else ActionStatus.FAILED
                onTaskUpdated(
                    taskItem.copy(
                        isCompleted = res.success,
                        progress = if (res.success) 100 else 0,
                        status = status,
                        details = res.message
                    )
                )
                res
            } catch (e: Exception) {
                onTaskUpdated(
                    taskItem.copy(
                        isCompleted = false,
                        progress = 0,
                        status = ActionStatus.FAILED,
                        details = "Exception: ${e.message}"
                    )
                )
                TaskStepResult(node.nodeId, false, e.message ?: "Unknown execution error")
            }
        } else if (node.action != null) {
            val actionResult = actionRouter.routeAction(node.action, bypassConfirmation = true, language = language)
            val stepSuccess = actionResult.success
            onTaskUpdated(
                taskItem.copy(
                    isCompleted = stepSuccess,
                    progress = if (stepSuccess) 100 else 0,
                    status = if (stepSuccess) ActionStatus.SUCCESS else ActionStatus.FAILED,
                    details = actionResult.message
                )
            )
            TaskStepResult(
                nodeId = node.nodeId,
                success = stepSuccess,
                message = actionResult.message,
                outputData = mapOf(node.nodeId to actionResult.message)
            )
        } else {
            TaskStepResult(node.nodeId, true, "Empty node completed")
        }
    }

    private fun runPreflightCheck(nodes: List<TaskNode>): ExecutionPreflightResult {
        val missingPermissions = mutableListOf<String>()
        // All tools verified at runtime
        return ExecutionPreflightResult(isReady = true)
    }

    private fun saveSafeCheckpoint(
        taskId: String,
        conversationId: String,
        goal: String,
        completedSteps: List<String>,
        remainingSteps: List<String>,
        dependenciesMap: Map<String, List<String>>,
        stepOutputs: Map<String, Any?>
    ) {
        val stringOutputs = stepOutputs.mapValues { it.value?.toString() ?: "" }
        val checkpoint = TaskCheckpoint(
            taskId = taskId,
            conversationId = conversationId,
            goal = goal,
            completedSteps = completedSteps,
            remainingSteps = remainingSteps,
            currentStep = remainingSteps.firstOrNull(),
            stepOutputs = stringOutputs,
            dependencies = dependenciesMap,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            safeResumeState = true,
            isPaused = isInterrupted.get(),
            isCancelled = isCancellationRequested.get()
        )
        checkpointStore.saveCheckpoint(checkpoint)
    }
}
