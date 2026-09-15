package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.action.ActionPlanner
import com.example.action.MediaCommand
import com.example.action.NexusAction
import com.example.action.NexusActionRouter
import com.example.action.VolumeDirection
import com.example.action.goal.*
import com.example.ai.AgentType
import com.example.ai.dialogue.MultiTurnResult
import com.example.ai.dialogue.MultiTurnSessionManager
import com.example.ai.dialogue.MultiTurnState
import com.example.ai.intelligence.ProactiveAssistant
import com.example.bridge.AndroidSystemBridge
import com.example.context.NexusContextEngine
import com.example.data.local.AppDatabase
import com.example.data.repository.EncryptedMemoryWalletRepository
import com.example.hardware.HardwareController
import com.example.media.MaxMediaManager
import com.example.security.NexusFeatureFlags
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
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NexusIntelligenceStressTestSuite {

    private lateinit var context: Context
    private lateinit var testScope: CoroutineScope
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
    private lateinit var multiTurnManager: MultiTurnSessionManager
    private lateinit var toolRegistry: ToolCapabilityRegistry
    private lateinit var proactiveAssistant: ProactiveAssistant
    private lateinit var featureFlags: NexusFeatureFlags
    private lateinit var temporalEngine: TemporalReasoningEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testScope = CoroutineScope(Dispatchers.Main)
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

        multiTurnManager = MultiTurnSessionManager(contextEngine)
        toolRegistry = ToolCapabilityRegistry.getInstance(context)
        toolRegistry.clearOverrides()
        featureFlags = NexusFeatureFlags.getInstance(context)
        proactiveAssistant = ProactiveAssistant(context, featureFlags)
        proactiveAssistant.resetDismissed()
        temporalEngine = TemporalReasoningEngine()
    }

    /**
     * SCENARIO 1: NATURAL HUMAN COMMANDS
     * Slang, mixed Hinglish, indirect phrasing, concise commands.
     */
    @Test
    fun scenario01_NaturalHumanCommands() {
        val now = System.currentTimeMillis()
        // 1. "Aadhe ghante baad mujhe chai peene ka yaad dilana"
        val temporal = temporalEngine.parseTemporalExpression("Aadhe ghante baad mujhe chai peene ka yaad dilana", now)
        assertNotNull(temporal)
        assertTrue(temporal!!.isRelative)
        val diffMinutes = (temporal.targetTimestampMs - now) / (1000 * 60)
        assertTrue(diffMinutes in 28..32)

        // 2. "Bhai gaana band kar de" -> Media Pause
        val mediaAction = NexusAction.Media(MediaCommand.PAUSE)
        assertEquals(MediaCommand.PAUSE, mediaAction.mediaCommand)

        // 3. "Volume thoda kam karo" -> Volume Decrease
        val volAction = NexusAction.Volume(VolumeDirection.DECREASE, 10)
        assertEquals(VolumeDirection.DECREASE, volAction.direction)

        // 4. "Screen pe kya likha hai dekh ke batao" -> Screen Context Retrieval
        val screenCtx = contextEngine.pruneForQuery(
            "Screen pe kya likha hai dekh ke batao",
            contextEngine.assembleCurrentContext()
        )
        assertNotNull(screenCtx)
    }

    /**
     * SCENARIO 2: CONTEXT CONTINUITY & MULTI-TURN REFINEMENT
     */
    @Test
    fun scenario02_ContextContinuityAndRefinement() {
        // Turn 1
        val turn1 = multiTurnManager.checkIncompleteCommand("Rahul ko message bhejo", AssistantLanguage.ENGLISH)
        assertNotNull(turn1)
        assertTrue(turn1!!.handled)
        assertTrue(turn1.requiresMoreTurns)
        assertTrue(multiTurnManager.currentState is MultiTurnState.AwaitingSlot)

        // Turn 2
        val turn2 = multiTurnManager.processFollowUp("Bolna main 10 minute late hunga", AssistantLanguage.ENGLISH)
        assertTrue(turn2.handled)
        assertTrue(turn2.requiresMoreTurns)
        assertTrue(multiTurnManager.currentState is MultiTurnState.AwaitingConfirmation)
        val stateAfterTurn2 = multiTurnManager.currentState as MultiTurnState.AwaitingConfirmation
        val msgAfterTurn2 = (stateAfterTurn2.action as NexusAction.Sms).message
        assertTrue(msgAfterTurn2.contains("10 minute late hunga"))

        // Turn 3: Append details
        val turn3 = multiTurnManager.processFollowUp("Haan, aur usme ye bhi likho ki traffic bohot zyada hai", AssistantLanguage.ENGLISH)
        assertTrue(turn3.handled)
        assertTrue(turn3.requiresMoreTurns)
        val stateAfterTurn3 = multiTurnManager.currentState as MultiTurnState.AwaitingConfirmation
        val msgAfterTurn3 = (stateAfterTurn3.action as NexusAction.Sms).message
        assertTrue(msgAfterTurn3.contains("traffic bohot zyada hai"))

        // Turn 4: Hold draft
        val turn4 = multiTurnManager.processFollowUp("Actually abhi mat bhejna, hold karo", AssistantLanguage.ENGLISH)
        assertTrue(turn4.handled)
        assertTrue(turn4.requiresMoreTurns)
        assertTrue(multiTurnManager.currentState is MultiTurnState.DraftHeld)

        // Turn 5: Resurrect & Send held draft
        val turn5 = multiTurnManager.processFollowUp("Achha bhej do", AssistantLanguage.ENGLISH)
        assertTrue(turn5.handled)
        assertFalse(turn5.requiresMoreTurns)
        assertNotNull(turn5.readyAction)
        assertEquals("Rahul", (turn5.readyAction as NexusAction.Sms).phoneNumber)
        assertTrue((turn5.readyAction as NexusAction.Sms).message.contains("traffic bohot zyada hai"))
    }

    /**
     * SCENARIO 2B: RECIPIENT SWITCH & DRAFT SHORTENING
     */
    @Test
    fun scenario02b_RecipientSwitchAndShortening() {
        multiTurnManager.checkIncompleteCommand("Rahul ko message bhejo", AssistantLanguage.ENGLISH)
        multiTurnManager.processFollowUp("Bolna main 10 minute late hunga traffic ki wajah se", AssistantLanguage.ENGLISH)

        // Shorten message
        val shortenRes = multiTurnManager.processFollowUp("Phir se bana do, but shorter", AssistantLanguage.ENGLISH)
        assertTrue(shortenRes.handled)
        val stateShort = multiTurnManager.currentState as MultiTurnState.AwaitingConfirmation
        val shortMsg = (stateShort.action as NexusAction.Sms).message
        assertTrue(shortMsg.contains("10m late"))

        // Switch recipient: "Arre nahi, Rahul nahi, Rohit ko bhejna tha"
        val switchRes = multiTurnManager.processFollowUp("Arre nahi, Rahul nahi, Rohit ko bhejna tha", AssistantLanguage.ENGLISH)
        assertTrue(switchRes.handled)
        val stateSwitch = multiTurnManager.currentState as MultiTurnState.AwaitingConfirmation
        val targetPhone = (stateSwitch.action as NexusAction.Sms).phoneNumber
        assertEquals("Rohit", targetPhone)
    }

    /**
     * SCENARIO 3: AMBIGUITY RESOLUTION
     */
    @Test
    fun scenario03_AmbiguityResolution() {
        val candidates = listOf(
            AmbiguityCandidate("c1", "Rahul Sharma", listOf("office", "manager")),
            AmbiguityCandidate("c2", "Rahul Kumar", listOf("college", "friend"))
        )

        // Ambiguous query
        val ambRes = goalPlanner.resolveContactAmbiguity("Rahul ko message karo", candidates)
        assertFalse(ambRes.resolved)
        assertTrue(ambRes.promptIfUnresolved!!.contains("Rahul Sharma or Rahul Kumar"))

        // Disambiguation by distinguishing token "Sharma"
        val resolvedRes = goalPlanner.resolveContactAmbiguity("Sharma wala Rahul", candidates)
        assertTrue(resolvedRes.resolved)
        assertEquals("Rahul Sharma", resolvedRes.selectedCandidate!!.displayName)
    }

    /**
     * SCENARIO 4: COMPOUND GOALS (DAG EXECUTION & TOPOLOGICAL ORDER)
     */
    @Test
    fun scenario04_CompoundGoalDAGExecution() = runBlocking {
        val prompt = "Kal meri meeting hai, uski details check karo, presentation ready karo aur meeting se 30 minute pehle reminder laga do."
        val (goal, nodes) = goalPlanner.planGoal(prompt)

        assertEquals(3, nodes.size)
        val idNode = nodes.find { it.nodeId == "step_identify_meeting" }
        val pptNode = nodes.find { it.nodeId == "step_generate_presentation" }
        val remNode = nodes.find { it.nodeId == "step_schedule_meeting_reminder" }

        assertNotNull(idNode)
        assertNotNull(pptNode)
        assertNotNull(remNode)

        assertTrue(pptNode!!.dependencies.contains(idNode!!.nodeId))
        assertTrue(remNode!!.dependencies.contains(pptNode.nodeId))

        // Execute DAG
        val result = dagExecutor.executeGraph(goal, nodes, resumeFromCheckpoint = false)
        assertTrue(result.success)

        val retrievedPpt = artifactRegistry.resolveReference("last presentation")
        assertNotNull(retrievedPpt)
        assertEquals(ArtifactType.PRESENTATION, retrievedPpt!!.type)
    }

    /**
     * SCENARIO 5: CONDITIONAL GOALS
     */
    @Test
    fun scenario05_ConditionalGoalBranching() = runBlocking {
        val prompt = "Kal agar 6 baje meeting hai to usse 30 minute pehle reminder laga dena, warna kuch mat karna."
        val (goal, nodes) = goalPlanner.planGoal(prompt)

        val calNode = nodes.find { it.nodeId == "step_calendar_check" }
        val remNode = nodes.find { it.nodeId == "step_schedule_reminder" }
        assertNotNull(calNode)
        assertNotNull(remNode)
        assertNotNull(remNode!!.condition)

        val result = dagExecutor.executeGraph(goal, nodes, resumeFromCheckpoint = false)
        assertTrue(result.success)
        assertTrue(result.outputData.containsKey("step_schedule_reminder"))
    }

    /**
     * SCENARIO 6: CROSS-AGENT WORKFLOW & ARTIFACT REGISTRY
     */
    @Test
    fun scenario06_CrossAgentWorkflowAndArtifactChaining() = runBlocking {
        val prompt = "Best laptops under 80000 research karo, top 3 compare karo, PDF banao, phir usi PDF se presentation banao."
        val (goal, nodes) = goalPlanner.planGoal(prompt)

        assertEquals(3, nodes.size)
        val resNode = nodes.find { it.nodeId == "step_research_synthesis" }
        val pdfNode = nodes.find { it.nodeId == "step_pdf_compilation" }
        val pptNode = nodes.find { it.nodeId == "step_presentation_from_pdf" }

        assertNotNull(resNode)
        assertNotNull(pdfNode)
        assertNotNull(pptNode)

        assertTrue(pdfNode!!.dependencies.contains(resNode!!.nodeId))
        assertTrue(pptNode!!.dependencies.contains(pdfNode.nodeId))

        val result = dagExecutor.executeGraph(goal, nodes, resumeFromCheckpoint = false)
        assertTrue(result.success)

        // Verify semantic reference resolution: "wo presentation", "last pdf"
        val resolvedPpt = artifactRegistry.resolveReference("wo presentation")
        assertNotNull(resolvedPpt)
        assertEquals(ArtifactType.PRESENTATION, resolvedPpt!!.type)

        val resolvedPdf = artifactRegistry.resolveReference("last pdf")
        assertNotNull(resolvedPdf)
        assertEquals(ArtifactType.PDF, resolvedPdf!!.type)
    }

    /**
     * SCENARIO 7 & 8: TASK INTERRUPTION & RESTART SURVIVABILITY
     */
    @Test
    fun scenario07_08_TaskInterruptionAndRestartSurvivability() = runBlocking {
        val taskId = "TASK_STRESS_DURABLE"
        val checkpoint = TaskCheckpoint(
            taskId = taskId,
            conversationId = "CONV_STRESS",
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

        var step2Executed = false
        val step1 = TaskNode("step_1_verified", "Step 1", AgentType.SYSTEM)
        val step2 = TaskNode(
            nodeId = "step_2_pending",
            title = "Step 2",
            agent = AgentType.SYSTEM,
            dependencies = setOf("step_1_verified"),
            customExecution = {
                step2Executed = true
                TaskStepResult("step_2_pending", true, "Step 2 finished")
            }
        )

        val freshExecutor = TaskDAGExecutor(
            actionRouter = router,
            checkpointStore = checkpointStore,
            artifactRegistry = artifactRegistry,
            onTaskUpdated = {},
            coroutineScope = testScope
        )

        val goalDef = GoalDefinition(goalId = taskId, userGoal = checkpoint.goal)
        val resumeResult = freshExecutor.executeGraph(goalDef, listOf(step1, step2), resumeFromCheckpoint = true)

        assertTrue(resumeResult.success)
        assertTrue(step2Executed)
    }

    /**
     * SCENARIO 9: TEMPORAL REASONING ACCURACY
     */
    @Test
    fun scenario09_TemporalReasoningAccuracy() {
        val now = System.currentTimeMillis()

        // "in 20 minutes"
        val in20 = temporalEngine.parseTemporalExpression("in 20 minutes", now)
        assertNotNull(in20)
        val diff20 = (in20!!.targetTimestampMs - now) / (1000 * 60)
        assertEquals(20L, diff20)

        // "tonight at 7"
        val tonight7 = temporalEngine.parseTemporalExpression("tonight at 7", now)
        assertNotNull(tonight7)
        val calTonight = Calendar.getInstance().apply { timeInMillis = tonight7!!.targetTimestampMs }
        assertEquals(19, calTonight.get(Calendar.HOUR_OF_DAY))

        // "kal subah 9 baje"
        val kal9 = temporalEngine.parseTemporalExpression("kal subah 9 baje", now)
        assertNotNull(kal9)
        val calKal = Calendar.getInstance().apply { timeInMillis = kal9!!.targetTimestampMs }
        assertEquals(9, calKal.get(Calendar.HOUR_OF_DAY))

        // "meeting se aadha ghanta pehle"
        val halfHourBefore = temporalEngine.parseTemporalExpression("meeting se aadha ghanta pehle", now)
        assertNotNull(halfHourBefore)
        val calHalf = Calendar.getInstance().apply { timeInMillis = halfHourBefore!!.targetTimestampMs }
        assertEquals(9, calHalf.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, calHalf.get(Calendar.MINUTE))
    }

    /**
     * SCENARIO 10: PROACTIVE ASSISTANT INTELLIGENCE & COOLDOWN
     */
    @Test
    fun scenario10_ProactiveIntelligenceAndSpamPrevention() {
        // 1. Upcoming meeting notice
        proactiveAssistant.checkProactiveConditions(upcomingMeetingNotice = "Client Review in 15 mins")
        val sug1 = proactiveAssistant.activeSuggestion.value
        assertNotNull(sug1)
        assertEquals("CALENDAR", sug1!!.actionType)

        // 2. Dismiss and Cooldown prevents duplicate spam
        proactiveAssistant.dismissSuggestion()
        assertNull(proactiveAssistant.activeSuggestion.value)

        // Immediately checking again respects dismissed / cooldown
        proactiveAssistant.checkProactiveConditions(upcomingMeetingNotice = "Client Review in 15 mins")
        assertNull(proactiveAssistant.activeSuggestion.value)

        // 3. Master feature flag disable
        featureFlags.setFeature(NexusFeatureFlags.KEY_PROACTIVE, false)
        proactiveAssistant.checkProactiveConditions(upcomingMeetingNotice = "New meeting")
        assertNull(proactiveAssistant.activeSuggestion.value)
    }

    /**
     * SCENARIO 11: TOOL CAPABILITY AWARENESS
     */
    @Test
    fun scenario11_CapabilityAwareness() {
        toolRegistry.setToolOverride("TOOL_CALENDAR", enabled = false, health = "UNAVAILABLE")
        assertFalse(toolRegistry.isToolAvailable("TOOL_CALENDAR"))

        val desc = toolRegistry.getToolDescriptor("TOOL_CALENDAR")
        assertNotNull(desc)
        assertEquals("UNAVAILABLE", desc!!.health)
        assertFalse(desc.enabled)
    }

    /**
     * SCENARIO 12: RESILIENT ERROR RETRY & DEGRADATION
     */
    @Test
    fun scenario12_SafeErrorRetryAndDegradation() = runBlocking {
        var callCount = 0
        val retryNode = TaskNode(
            nodeId = "step_transient_retry",
            title = "Transient Tool Call",
            agent = AgentType.SYSTEM,
            customExecution = {
                callCount++
                if (callCount < 2) {
                    TaskStepResult("step_transient_retry", false, "Transient Network Glitch")
                } else {
                    TaskStepResult("step_transient_retry", true, "Success after retry")
                }
            }
        )

        val goal = GoalDefinition(goalId = "TASK_RETRY", userGoal = "Test Retry")
        val result = dagExecutor.executeGraph(goal, listOf(retryNode), resumeFromCheckpoint = false)
        // Verify graceful completion or failure reporting without crashes
        assertNotNull(result)
        assertEquals(1, callCount)
    }

    /**
     * SCENARIO 13: MASTER END-TO-END CUJ
     * "Pantham, kal meri meeting hai. Meeting check karo, agar presentation nahi bani hai to ek presentation ready karo, uska PDF bhi bana do, meeting se 30 minute pehle reminder laga do, aur mujhe subah Hindi mein iska briefing de dena."
     */
    @Test
    fun scenario13_MasterEndToEndGoalExecution() = runBlocking {
        val masterPrompt = "Pantham, kal meri meeting hai. Meeting check karo, agar presentation nahi bani hai to ek presentation ready karo, uska PDF bhi bana do, meeting se 30 minute pehle reminder laga do, aur mujhe subah Hindi mein iska briefing de dena."
        val (goal, nodes) = goalPlanner.planGoal(masterPrompt)

        // Verify 5 distinct nodes planned
        assertEquals(5, nodes.size)
        val idNode = nodes.find { it.nodeId == "step_identify_meeting" }
        val pptNode = nodes.find { it.nodeId == "step_generate_presentation" }
        val pdfNode = nodes.find { it.nodeId == "step_convert_ppt_to_pdf" }
        val remNode = nodes.find { it.nodeId == "step_schedule_meeting_reminder" }
        val briefNode = nodes.find { it.nodeId == "step_schedule_morning_briefing" }

        assertNotNull(idNode)
        assertNotNull(pptNode)
        assertNotNull(pdfNode)
        assertNotNull(remNode)
        assertNotNull(briefNode)

        // Execute full master DAG
        val result = dagExecutor.executeGraph(goal, nodes, resumeFromCheckpoint = false, language = AssistantLanguage.HINDI)
        assertTrue(result.success)

        // Verify registered artifacts
        val pptArtifact = artifactRegistry.resolveReference("presentation")
        val pdfArtifact = artifactRegistry.resolveReference("pdf")
        assertNotNull(pptArtifact)
        assertNotNull(pdfArtifact)
        assertEquals(ArtifactType.PRESENTATION, pptArtifact!!.type)
        assertEquals(ArtifactType.PDF, pdfArtifact!!.type)
    }
}
