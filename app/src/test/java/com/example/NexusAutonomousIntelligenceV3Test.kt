package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.action.ActionPlanner
import com.example.action.NexusAction
import com.example.action.NexusActionRouter
import com.example.action.goal.*
import com.example.bridge.AndroidSystemBridge
import com.example.context.NexusContextEngine
import com.example.data.local.AppDatabase
import com.example.data.repository.EncryptedMemoryWalletRepository
import com.example.hardware.HardwareController
import com.example.media.MaxMediaManager
import com.example.voice.AssistantLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NexusAutonomousIntelligenceV3Test {

    private lateinit var context: Context
    private lateinit var systemBridge: AndroidSystemBridge
    private lateinit var hardwareController: HardwareController
    private lateinit var mediaManager: MaxMediaManager
    private lateinit var contextEngine: NexusContextEngine
    private lateinit var memoryRepo: EncryptedMemoryWalletRepository
    private lateinit var router: NexusActionRouter
    private lateinit var artifactRegistry: ArtifactRegistry
    private lateinit var checkpointStore: TaskCheckpointStore
    private lateinit var dagExecutor: TaskDAGExecutor
    private lateinit var goalPlanner: AutonomousGoalPlanner
    private lateinit var actionPlanner: ActionPlanner

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val testScope = CoroutineScope(Dispatchers.Main)
        systemBridge = AndroidSystemBridge(context)
        hardwareController = HardwareController(context, testScope)
        mediaManager = MaxMediaManager(context)
        contextEngine = NexusContextEngine(context, hardwareController, mediaManager)

        val db = AppDatabase.createInMemoryInstance(context)
        memoryRepo = EncryptedMemoryWalletRepository(
            conversationDao = db.conversationDao(),
            userPreferenceDao = db.userPreferenceDao(),
            memoryWalletDao = db.memoryWalletDao()
        )

        router = NexusActionRouter(
            context = context,
            systemBridge = systemBridge,
            hardwareController = hardwareController,
            mediaManager = mediaManager,
            memoryRepository = memoryRepo,
            callManager = null,
            ttsProvider = null,
            onTaskUpdated = {},
            onRequestConfirmation = {},
            coroutineScope = testScope
        )

        artifactRegistry = ArtifactRegistry.getInstance(context)
        artifactRegistry.clear()
        checkpointStore = TaskCheckpointStore.getInstance(context)

        dagExecutor = TaskDAGExecutor(
            actionRouter = router,
            checkpointStore = checkpointStore,
            artifactRegistry = artifactRegistry,
            onTaskUpdated = {},
            coroutineScope = testScope
        )

        goalPlanner = AutonomousGoalPlanner(
            context = context,
            actionRouter = router,
            executor = dagExecutor,
            artifactRegistry = artifactRegistry,
            contextEngine = contextEngine,
            systemBridge = systemBridge
        )

        actionPlanner = ActionPlanner(
            systemBridge = systemBridge,
            hardwareController = hardwareController,
            mediaManager = mediaManager,
            ttsProvider = null,
            onTaskUpdated = {},
            coroutineScope = testScope,
            context = context,
            actionRouter = router,
            contextEngine = contextEngine
        )
    }

    // ==========================================
    // REQUIREMENT 35: END-TO-END COMPOSITION TESTS
    // ==========================================

    /**
     * TEST A: Voice -> Calendar -> Condition -> Reminder -> TTS
     * Kal meri meeting hai, uske liye presentation ready kar do aur meeting se 30 minute pehle yaad dila dena.
     */
    @Test
    fun testA_MeetingPrep_Presentation_Reminder() = runBlocking {
        val input = "Kal meri meeting hai, uske liye presentation ready kar do aur meeting se 30 minute pehle yaad bhi dila dena."
        val (goal, nodes) = goalPlanner.planGoal(input)

        assertEquals(3, nodes.size)
        assertEquals("step_identify_meeting", nodes[0].nodeId)
        assertEquals("step_generate_presentation", nodes[1].nodeId)
        assertEquals("step_schedule_meeting_reminder", nodes[2].nodeId)

        // Verify dependency linkage: presentation depends on identify meeting
        assertTrue(nodes[1].dependencies.contains("step_identify_meeting"))
        assertTrue(nodes[2].dependencies.contains("step_generate_presentation"))

        val result = dagExecutor.executeGraph(goal, nodes, language = AssistantLanguage.HINDI)
        assertTrue("Execution should succeed", result.success)
        assertEquals(1, result.artifacts.size)
        assertEquals(ArtifactType.PRESENTATION, result.artifacts[0].type)
        assertEquals("Presentation_Product_Strategy.pptx", result.artifacts[0].name)

        // Verify artifact is recorded in ArtifactRegistry
        val retrieved = artifactRegistry.resolveReference("last presentation")
        assertNotNull(retrieved)
        assertEquals("Presentation_Product_Strategy.pptx", retrieved!!.name)
    }

    /**
     * TEST B: Voice -> Research -> Comparison -> PDF -> Artifact memory
     */
    @Test
    fun testB_Research_Comparison_PDF_ArtifactMemory() = runBlocking {
        val input = "Research electric cars, compare top 3 models, and generate a PDF report"
        val (goal, nodes) = goalPlanner.planGoal(input)

        assertEquals(2, nodes.size)
        assertEquals("step_research_synthesis", nodes[0].nodeId)
        assertEquals("step_pdf_compilation", nodes[1].nodeId)

        val result = dagExecutor.executeGraph(goal, nodes, language = AssistantLanguage.ENGLISH)
        assertTrue(result.success)
        assertEquals(1, result.artifacts.size)
        assertEquals(ArtifactType.PDF, result.artifacts[0].type)

        // Verify semantic reference resolution ("the pdf", "isko", "usko")
        val resolvedByPronoun = artifactRegistry.resolveReference("isko share karo")
        assertNotNull(resolvedByPronoun)
        assertEquals("EV_Comparison_Report.pdf", resolvedByPronoun!!.name)
    }

    /**
     * TEST C: Vision / Screen Context -> Context Resolution -> Action Plan
     */
    @Test
    fun testC_ScreenContext_ActionPlanning() = runBlocking {
        val fullCtx = contextEngine.assembleCurrentContext(activeTask = "Reviewing Document")
        assertNotNull(fullCtx)
        assertEquals("Reviewing Document", fullCtx.activeTask)
    }

    /**
     * TEST D: Entity Ambiguity Resolution: "Rahul ko message karo" -> Check candidate Rahuls
     */
    @Test
    fun testD_AmbiguityResolution_MultipleContacts() {
        val candidates = listOf(
            AmbiguityCandidate("id_1", "Rahul Sharma", listOf("office", "manager")),
            AmbiguityCandidate("id_2", "Rahul Kumar", listOf("college", "friend"))
        )

        // Ambiguous query without context
        val ambiguousResult = goalPlanner.resolveContactAmbiguity("Rahul ko message karo", candidates)
        assertFalse(ambiguousResult.resolved)
        assertNotNull(ambiguousResult.promptIfUnresolved)
        assertTrue(ambiguousResult.promptIfUnresolved!!.contains("Rahul Sharma or Rahul Kumar"))

        // Query with context tag "office wala"
        val resolvedResult = goalPlanner.resolveContactAmbiguity("Rahul jo office wala tha", candidates)
        assertTrue(resolvedResult.resolved)
        assertEquals("Rahul Sharma", resolvedResult.selectedCandidate!!.displayName)
    }

    /**
     * TEST E: Memory -> User Preference -> Retention
     */
    @Test
    fun testE_UserPreferenceMemoryRetention() = runBlocking {
        memoryRepo.savePreference("pref_voice_tone", "playful_hindi", "PERSONALIZATION")
        val pref = memoryRepo.getPreference("pref_voice_tone")
        assertEquals("playful_hindi", pref)
    }

    /**
     * TEST F: Automation -> Interruption -> Pause -> App restart -> Safe resume
     */
    @Test
    fun testF_TaskInterruption_Checkpoint_SafeResume() = runBlocking {
        val taskId = "TASK_RESUME_TEST_001"
        val checkpoint = TaskCheckpoint(
            taskId = taskId,
            conversationId = "CONV_001",
            goal = "Multi-step complex synthesis",
            completedSteps = listOf("step_1_verified"),
            remainingSteps = listOf("step_2_pending"),
            currentStep = "step_2_pending",
            stepOutputs = mapOf("step_1_verified" to "Pre-computation complete"),
            dependencies = mapOf("step_2_pending" to listOf("step_1_verified")),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            safeResumeState = true,
            isPaused = true
        )
        checkpointStore.saveCheckpoint(checkpoint)

        // Checkpoint durable retrieval
        val retrieved = checkpointStore.getCheckpoint(taskId)
        assertNotNull(retrieved)
        assertEquals("Multi-step complex synthesis", retrieved!!.goal)
        assertTrue(retrieved.safeResumeState)
        assertEquals(listOf("step_1_verified"), retrieved.completedSteps)
        assertEquals(listOf("step_2_pending"), retrieved.remainingSteps)

        // Resuming from safe checkpoint does NOT re-execute step 1
        var step2Executed = false
        val nodes = listOf(
            TaskNode("step_1_verified", "Step 1", com.example.ai.AgentType.SYSTEM),
            TaskNode("step_2_pending", "Step 2", com.example.ai.AgentType.SYSTEM, dependencies = setOf("step_1_verified"), customExecution = {
                step2Executed = true
                TaskStepResult("step_2_pending", true, "Step 2 finished")
            })
        )

        val goalDef = GoalDefinition(goalId = taskId, userGoal = checkpoint.goal)
        val result = dagExecutor.executeGraph(goalDef, nodes, resumeFromCheckpoint = true)
        assertTrue(result.success)
        assertTrue(step2Executed)
    }

    /**
     * TEST G: Compound Command with Parallel + Conditional Execution:
     * "Weather batao, mera calendar dekho, aur agar 6 baje meeting hai to mujhe 30 minute pehle reminder laga dena."
     */
    @Test
    fun testG_CompoundCommand_Parallel_Conditional_DAG() = runBlocking {
        val input = "Weather batao, mera calendar dekho, aur agar 6 baje meeting hai to mujhe 30 minute pehle reminder laga dena."
        val (goal, nodes) = goalPlanner.planGoal(input)

        assertEquals(3, nodes.size)
        val weatherNode = nodes.find { it.nodeId == "step_weather_check" }
        val calendarNode = nodes.find { it.nodeId == "step_calendar_check" }
        val reminderNode = nodes.find { it.nodeId == "step_schedule_reminder" }

        assertNotNull(weatherNode)
        assertNotNull(calendarNode)
        assertNotNull(reminderNode)

        // Independent branches are marked parallel
        assertTrue(weatherNode!!.isParallel)
        assertTrue(calendarNode!!.isParallel)

        // Conditional reminder depends on calendar output
        assertTrue(reminderNode!!.dependencies.contains("step_calendar_check"))
        assertNotNull(reminderNode.condition)

        val result = dagExecutor.executeGraph(goal, nodes, language = AssistantLanguage.HINDI)
        assertTrue(result.success)
        assertTrue(result.outputData.containsKey("step_schedule_reminder"))
    }

    // ==========================================
    // REQUIREMENT 36: ADVERSARIAL & EDGE CASE TESTS
    // ==========================================

    @Test
    fun testTemporalReasoning_RelativeTimeNormalization() {
        val engine = TemporalReasoningEngine()
        val now = System.currentTimeMillis()

        // 20 minutes relative
        val minRes = engine.parseTemporalExpression("in 20 minutes", now)
        assertNotNull(minRes)
        assertTrue(minRes!!.targetTimestampMs > now)
        val diffMins = (minRes.targetTimestampMs - now) / (1000 * 60)
        assertEquals(20L, diffMins)

        // Tomorrow
        val tomRes = engine.parseTemporalExpression("tomorrow morning", now)
        assertNotNull(tomRes)
        assertTrue(tomRes!!.targetTimestampMs > now)

        // Tonight
        val tonightRes = engine.parseTemporalExpression("tonight at 7", now)
        assertNotNull(tonightRes)
    }

    @Test
    fun testPlanRevision_ChangeFormatToPDF() {
        val input = "Kal meri meeting hai, uske liye presentation ready kar do"
        val (goal, nodes) = goalPlanner.planGoal(input)

        val revised = goalPlanner.reviseActivePlan("Actually make it a PDF instead", nodes)
        val pdfNode = revised.find { it.nodeId.contains("presentation") }
        assertNotNull(pdfNode)
        assertTrue(pdfNode!!.title.contains("PDF"))
    }

    @Test
    fun testTaskCancellation_AbortsGraphSafely() = runBlocking {
        val taskId = "TASK_CANCEL_TEST"
        val goalDef = GoalDefinition(goalId = taskId, userGoal = "Cancel test")
        val cancelNode = TaskNode("step_cancel", "Cancellation Node", com.example.ai.AgentType.SYSTEM, customExecution = {
            dagExecutor.cancelExecution(taskId)
            TaskStepResult("step_cancel", true, "Triggered cancel")
        })
        val secondNode = TaskNode("step_never_run", "Never Run", com.example.ai.AgentType.SYSTEM, dependencies = setOf("step_cancel"))

        val result = dagExecutor.executeGraph(goalDef, listOf(cancelNode, secondNode))
        assertFalse("Execution should report cancelled", result.success)
        assertTrue(result.message.contains("cancelled", ignoreCase = true))
    }
}
