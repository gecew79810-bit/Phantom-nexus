package com.pantham.nexus.intelligence.execution

import com.pantham.nexus.intelligence.model.AgentRequest
import com.pantham.nexus.intelligence.model.AgentResult
import com.pantham.nexus.intelligence.model.NexusContext
import com.pantham.nexus.intelligence.model.TaskNode
import com.pantham.nexus.intelligence.model.TaskPlan
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

interface IntelligenceAgent {

    suspend fun execute(
        request: AgentRequest
    ): AgentResult
}

interface IntelligenceAgentRouter {

    fun resolve(
        node: TaskNode
    ): IntelligenceAgent?
}

class NexusDAGExecutor(
    private val agentRouter: IntelligenceAgentRouter
) {

    suspend fun execute(
        plan: TaskPlan,
        context: NexusContext
    ): DAGExecutionResult =
        coroutineScope {

            val completed =
                mutableSetOf<String>()

            val outputs =
                mutableMapOf<String, AgentResult>()

            val failed =
                mutableSetOf<String>()

            val remaining =
                plan.nodes.toMutableList()

            while (remaining.isNotEmpty()) {

                val executable =
                    remaining.filter { node ->

                        node.dependencies.all {
                            completed.contains(it)
                        } &&
                        conditionAllows(
                            node,
                            outputs
                        )
                    }

                if (executable.isEmpty()) {

                    return@coroutineScope DAGExecutionResult(
                        completed = completed,
                        failed = failed,
                        outputs = outputs,
                        deadlocked = true
                    )
                }

                val waveResults =
                    executable
                        .map { node ->

                            async {

                                val agent =
                                    agentRouter.resolve(
                                        node
                                    )

                                if (agent == null) {

                                    node.id to AgentResult(
                                        success = false,
                                        message =
                                            "No agent available for ${node.intent}",
                                        shouldReplan = false,
                                        failureReason =
                                            "AGENT_UNAVAILABLE"
                                    )

                                } else {

                                    node.id to
                                        runCatching {
                                            agent.execute(
                                                AgentRequest(
                                                    taskId =
                                                        plan.taskId,
                                                    node = node,
                                                    context =
                                                        context
                                                )
                                            )
                                        }.getOrElse { error ->

                                            AgentResult(
                                                success = false,
                                                message =
                                                    "Agent execution failed",
                                                failureReason =
                                                    error.message
                                            )
                                        }
                                }
                            }
                        }
                        .awaitAll()

                for ((nodeId, result) in waveResults) {

                    outputs[nodeId] = result

                    if (result.success) {
                        completed += nodeId
                    } else {
                        failed += nodeId
                    }
                }

                remaining.removeAll(
                    executable.toSet()
                )

                if (waveResults.any { !it.second.success }) {
                    break
                }
            }

            DAGExecutionResult(
                completed = completed,
                failed = failed,
                outputs = outputs,
                deadlocked = false
            )
        }

    private fun conditionAllows(
        node: TaskNode,
        outputs: Map<String, AgentResult>
    ): Boolean {

        return when (
            val condition = node.condition
        ) {

            null -> true

            is com.pantham.nexus.intelligence.model
                .TaskCondition.Always -> true

            is com.pantham.nexus.intelligence.model
                .TaskCondition.NodeSucceeded -> {

                outputs[
                    condition.nodeId
                ]?.success == true
            }

            is com.pantham.nexus.intelligence.model
                .TaskCondition.NodeProducedValue -> {

                val result =
                    outputs[
                        condition.nodeId
                    ]

                val actual =
                    result
                        ?.output
                        ?.get(condition.key)
                        ?.toString()

                result?.success == true &&
                    (
                        condition.expected == null ||
                        actual == condition.expected
                    )
            }
        }
    }
}

data class DAGExecutionResult(
    val completed: Set<String>,
    val failed: Set<String>,
    val outputs: Map<String, AgentResult>,
    val deadlocked: Boolean
)
