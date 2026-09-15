package com.pantham.nexus.intelligence

import com.pantham.nexus.intelligence.context.NexusContextAssembler
import com.pantham.nexus.intelligence.intent.NexusIntentEngine
import com.pantham.nexus.intelligence.model.ConfidenceLevel
import com.pantham.nexus.intelligence.model.InputChannel
import com.pantham.nexus.intelligence.model.IntelligenceResponse
import com.pantham.nexus.intelligence.model.TaskCheckpoint
import com.pantham.nexus.intelligence.model.TaskStatus
import com.pantham.nexus.intelligence.persistence.CheckpointStore
import com.pantham.nexus.intelligence.planning.NexusGoalPlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class NexusIntelligenceCore(
    private val contextAssembler: NexusContextAssembler,
    private val intentEngine: NexusIntentEngine,
    private val planner: NexusGoalPlanner,
    private val checkpointStore: CheckpointStore,
    private val taskRuntime: NexusTaskRuntime
) {

    suspend fun process(
        input: String,
        channel: InputChannel = InputChannel.TEXT,
        conversationId: String = UUID.randomUUID().toString(),
        taskId: String? = null
    ): IntelligenceResponse =
        withContext(Dispatchers.Default) {

            val resolvedTaskId =
                taskId
                    ?: UUID.randomUUID().toString()

            val context =
                contextAssembler.build(
                    conversationId =
                        conversationId,
                    taskId =
                        resolvedTaskId,
                    input =
                        input,
                    channel =
                        channel
                )

            val intent =
                intentEngine.detect(
                    input
                )

            if (
                intent.confidenceLevel ==
                ConfidenceLevel.VERY_LOW
            ) {

                return@withContext IntelligenceResponse(
                    message =
                        "I need a little more information to understand that.",
                    taskId =
                        resolvedTaskId,
                    taskStatus =
                        TaskStatus.WAITING_FOR_INPUT,
                    requiresClarification =
                        true,
                    clarificationQuestion =
                        clarificationFor(
                            intent
                        )
                )
            }

            if (
                intent.missingSlots.isNotEmpty()
            ) {

                return@withContext IntelligenceResponse(
                    message =
                        "I need one more detail.",
                    taskId =
                        resolvedTaskId,
                    taskStatus =
                        TaskStatus.WAITING_FOR_INPUT,
                    requiresClarification =
                        true,
                    clarificationQuestion =
                        "Please provide: ${
                            intent.missingSlots.joinToString()
                        }."
                )
            }

            val plan =
                planner.buildPlan(
                    goal = input,
                    primaryIntent = intent
                )

            val checkpoint =
                TaskCheckpoint(
                    taskId = plan.taskId,
                    status = TaskStatus.PLANNING,
                    completedNodeIds =
                        emptySet(),
                    failedNodeIds =
                        emptySet(),
                    pendingNodeIds =
                        plan.nodes
                            .map { it.id }
                            .toSet(),
                    updatedAt =
                        System.currentTimeMillis()
                )

            checkpointStore.save(
                checkpoint
            )

            val result =
                taskRuntime.executePlan(
                    plan = plan,
                    context = context
                )

            if (result.requiresConfirmation) {

                return@withContext IntelligenceResponse(
                    message =
                        "I need your confirmation before I continue.",
                    taskId =
                        plan.taskId,
                    taskStatus =
                        TaskStatus.WAITING_FOR_CONFIRMATION,
                    requiresConfirmation =
                        true,
                    confirmationDescription =
                        result.confirmationDescription
                )
            }

            if (!result.success) {

                return@withContext IntelligenceResponse(
                    message =
                        result.message,
                    taskId =
                        plan.taskId,
                    taskStatus =
                        TaskStatus.FAILED
                )
            }

            checkpointStore.save(
                TaskCheckpoint(
                    taskId =
                        plan.taskId,
                    status =
                        TaskStatus.COMPLETED,
                    completedNodeIds =
                        plan.nodes.map { it.id }.toSet(),
                    failedNodeIds =
                        emptySet(),
                    pendingNodeIds =
                        emptySet(),
                    updatedAt =
                        System.currentTimeMillis()
                )
            )

            IntelligenceResponse(
                message =
                    result.message,
                taskId =
                    plan.taskId,
                taskStatus =
                    TaskStatus.COMPLETED,
                metadata =
                    result.metadata
            )
        }

    private fun clarificationFor(
        intent: com.pantham.nexus.intelligence.model.IntentResult
    ): String {

        return when (
            intent.type
        ) {
            com.pantham.nexus.intelligence.model.IntentType.SEND_MESSAGE ->
                "Who should I send it to, and what should the message say?"

            com.pantham.nexus.intelligence.model.IntentType.MAKE_CALL ->
                "Who should I call?"

            else ->
                "What would you like me to do?"
        }
    }
}

interface NexusTaskRuntime {

    suspend fun executePlan(
        plan: com.pantham.nexus.intelligence.model.TaskPlan,
        context: com.pantham.nexus.intelligence.model.NexusContext
    ): TaskRuntimeResult
}

data class TaskRuntimeResult(
    val success: Boolean,
    val message: String,
    val requiresConfirmation: Boolean = false,
    val confirmationDescription: String? = null,
    val metadata: Map<String, String> = emptyMap()
)
