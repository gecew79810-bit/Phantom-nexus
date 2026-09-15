package com.pantham.nexus.intelligence.execution

import com.pantham.nexus.intelligence.model.AgentRequest
import com.pantham.nexus.intelligence.model.AgentResult
import com.pantham.nexus.intelligence.model.IntentType
import com.pantham.nexus.intelligence.model.NexusContext
import com.pantham.nexus.intelligence.model.TaskCondition
import com.pantham.nexus.intelligence.model.TaskNode
import com.pantham.nexus.intelligence.model.TaskPlan
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusDAGExecutorTest {

    @Test
    fun testExecuteWaveAndDependencyResolution() = runBlocking {
        val node1 = TaskNode(name = "Step 1", intent = IntentType.RESEARCH)
        val node2 = TaskNode(name = "Step 2", intent = IntentType.DOCUMENT_GENERATION, dependencies = setOf(node1.id))
        val plan = TaskPlan(goal = "Research and generate", nodes = listOf(node1, node2))

        val router = object : IntelligenceAgentRouter {
            override fun resolve(node: TaskNode): IntelligenceAgent {
                return object : IntelligenceAgent {
                    override suspend fun execute(request: AgentRequest): AgentResult {
                        return AgentResult(success = true, message = "Executed ${request.node.name}")
                    }
                }
            }
        }

        val executor = NexusDAGExecutor(router)
        val result = executor.execute(plan, NexusContext())

        assertFalse(result.deadlocked)
        assertEquals(2, result.completed.size)
        assertTrue(result.completed.contains(node1.id))
        assertTrue(result.completed.contains(node2.id))
        assertEquals(0, result.failed.size)
    }

    @Test
    fun testConditionalExecutionWithNodeProducedValue() = runBlocking {
        val lookupNode = TaskNode(name = "Find Meeting", intent = IntentType.CALENDAR)
        val reminderNode = TaskNode(
            name = "Remind",
            intent = IntentType.REMINDER,
            dependencies = setOf(lookupNode.id),
            condition = TaskCondition.NodeProducedValue(
                nodeId = lookupNode.id,
                key = "meetingFound",
                expected = "true"
            )
        )
        val plan = TaskPlan(goal = "Calendar Flow", nodes = listOf(lookupNode, reminderNode))

        val router = object : IntelligenceAgentRouter {
            override fun resolve(node: TaskNode): IntelligenceAgent {
                return object : IntelligenceAgent {
                    override suspend fun execute(request: AgentRequest): AgentResult {
                        return if (request.node.id == lookupNode.id) {
                            AgentResult(
                                success = true,
                                output = mapOf("meetingFound" to "true")
                            )
                        } else {
                            AgentResult(success = true, message = "Reminder set")
                        }
                    }
                }
            }
        }

        val executor = NexusDAGExecutor(router)
        val result = executor.execute(plan, NexusContext())

        assertFalse(result.deadlocked)
        assertEquals(2, result.completed.size)
    }

    @Test
    fun testDeadlockDetectionWhenDependencyMissing() = runBlocking {
        val brokenNode = TaskNode(name = "Unreachable", intent = IntentType.RESEARCH, dependencies = setOf("non_existent_id"))
        val plan = TaskPlan(goal = "Broken Plan", nodes = listOf(brokenNode))

        val router = object : IntelligenceAgentRouter {
            override fun resolve(node: TaskNode): IntelligenceAgent? = null
        }

        val executor = NexusDAGExecutor(router)
        val result = executor.execute(plan, NexusContext())

        assertTrue(result.deadlocked)
        assertEquals(0, result.completed.size)
    }
}
