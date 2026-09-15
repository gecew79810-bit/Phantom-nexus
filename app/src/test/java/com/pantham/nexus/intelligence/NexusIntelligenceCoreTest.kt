package com.pantham.nexus.intelligence

import com.pantham.nexus.intelligence.context.MemorySource
import com.pantham.nexus.intelligence.context.NexusContextAssembler
import com.pantham.nexus.intelligence.context.NexusMemoryRelevanceEngine
import com.pantham.nexus.intelligence.context.NexusRuntimeContextProvider
import com.pantham.nexus.intelligence.context.StoredMemory
import com.pantham.nexus.intelligence.intent.NexusIntentEngine
import com.pantham.nexus.intelligence.intent.SemanticIntentCandidate
import com.pantham.nexus.intelligence.intent.SemanticIntentModel
import com.pantham.nexus.intelligence.model.ConfidenceLevel
import com.pantham.nexus.intelligence.model.InputChannel
import com.pantham.nexus.intelligence.model.IntentResult
import com.pantham.nexus.intelligence.model.IntentType
import com.pantham.nexus.intelligence.model.NexusContext
import com.pantham.nexus.intelligence.model.ResolvedEntity
import com.pantham.nexus.intelligence.model.RiskLevel
import com.pantham.nexus.intelligence.model.TaskCheckpoint
import com.pantham.nexus.intelligence.model.TaskPlan
import com.pantham.nexus.intelligence.model.TaskStatus
import com.pantham.nexus.intelligence.persistence.CheckpointStore
import com.pantham.nexus.intelligence.planning.NexusGoalPlanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusIntelligenceCoreTest {

    private val runtimeProvider = object : NexusRuntimeContextProvider {
        override suspend fun currentPackage(): String? = "com.example"
        override suspend fun currentScreenSummary(): String? = "Home"
        override suspend fun currentLocation(): String? = "Office"
        override suspend fun batteryPercent(): Int? = 85
        override suspend fun networkAvailable(): Boolean = true
        override suspend fun currentMedia(): String? = null
        override suspend fun recentEntities(): List<ResolvedEntity> = emptyList()
    }

    private val memorySource = object : MemorySource {
        override suspend fun getAllMemories(): List<StoredMemory> = listOf(
            StoredMemory(key = "assistant name", value = "Nexus", category = "identity")
        )
    }

    private val memoryEngine = NexusMemoryRelevanceEngine(memorySource)
    private val contextAssembler = NexusContextAssembler(runtimeProvider, memoryEngine)

    private val mockSemanticModel = object : SemanticIntentModel {
        override suspend fun classify(text: String): List<SemanticIntentCandidate> {
            return if (text.contains("spotify")) {
                listOf(SemanticIntentCandidate(IntentType.OPEN_APP, 0.95))
            } else {
                emptyList()
            }
        }
    }

    private val intentEngine = NexusIntentEngine(mockSemanticModel)
    private val planner = NexusGoalPlanner()

    private val inMemoryCheckpointStore = object : CheckpointStore {
        val storage = mutableMapOf<String, TaskCheckpoint>()

        override suspend fun save(checkpoint: TaskCheckpoint) {
            storage[checkpoint.taskId] = checkpoint
        }

        override suspend fun load(taskId: String): TaskCheckpoint? = storage[taskId]

        override suspend fun delete(taskId: String) {
            storage.remove(taskId)
        }
    }

    @Test
    fun testSuccessfulExecutionFlow() = runBlocking {
        val taskRuntime = object : NexusTaskRuntime {
            override suspend fun executePlan(plan: TaskPlan, context: NexusContext): TaskRuntimeResult {
                return TaskRuntimeResult(
                    success = true,
                    message = "Opened Spotify successfully",
                    metadata = mapOf("app" to "Spotify")
                )
            }
        }

        val core = NexusIntelligenceCore(
            contextAssembler = contextAssembler,
            intentEngine = intentEngine,
            planner = planner,
            checkpointStore = inMemoryCheckpointStore,
            taskRuntime = taskRuntime
        )

        val response = core.process(input = "open spotify", channel = InputChannel.VOICE)

        assertEquals(TaskStatus.COMPLETED, response.taskStatus)
        assertEquals("Opened Spotify successfully", response.message)
        assertFalse(response.requiresClarification)
        assertFalse(response.requiresConfirmation)

        // Verify checkpoint was completed
        val saved = inMemoryCheckpointStore.load(response.taskId)
        assertNotNull(saved)
        assertEquals(TaskStatus.COMPLETED, saved?.status)
    }

    @Test
    fun testMissingSlotsRequiresClarification() = runBlocking {
        val taskRuntime = object : NexusTaskRuntime {
            override suspend fun executePlan(plan: TaskPlan, context: NexusContext): TaskRuntimeResult {
                return TaskRuntimeResult(success = true, message = "Executed")
            }
        }

        val core = NexusIntelligenceCore(
            contextAssembler = contextAssembler,
            intentEngine = intentEngine,
            planner = planner,
            checkpointStore = inMemoryCheckpointStore,
            taskRuntime = taskRuntime
        )

        // "send message" is missing recipient and message text
        val response = core.process(input = "send message")

        assertEquals(TaskStatus.WAITING_FOR_INPUT, response.taskStatus)
        assertTrue(response.requiresClarification)
        assertNotNull(response.clarificationQuestion)
        assertTrue(response.clarificationQuestion!!.contains("recipient") || response.clarificationQuestion!!.contains("message"))
    }

    @Test
    fun testConfirmationFlow() = runBlocking {
        val taskRuntime = object : NexusTaskRuntime {
            override suspend fun executePlan(plan: TaskPlan, context: NexusContext): TaskRuntimeResult {
                return TaskRuntimeResult(
                    success = true,
                    message = "Ready to proceed",
                    requiresConfirmation = true,
                    confirmationDescription = "Are you sure you want to run this high risk task?"
                )
            }
        }

        val core = NexusIntelligenceCore(
            contextAssembler = contextAssembler,
            intentEngine = intentEngine,
            planner = planner,
            checkpointStore = inMemoryCheckpointStore,
            taskRuntime = taskRuntime
        )

        val response = core.process(input = "open spotify")

        assertEquals(TaskStatus.WAITING_FOR_CONFIRMATION, response.taskStatus)
        assertTrue(response.requiresConfirmation)
        assertEquals("Are you sure you want to run this high risk task?", response.confirmationDescription)
    }
}
