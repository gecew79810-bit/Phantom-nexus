package com.pantham.nexus.task

import com.example.action.goal.*
import com.example.ai.AgentType
import com.example.ai.TaskItem
import com.example.voice.AssistantLanguage
import com.pantham.nexus.decision.engine.NexusDecisionEngine
import com.pantham.nexus.learning.runtime.NexusAdaptiveLearningRuntime
import com.pantham.nexus.task.decision.TaskDecisionStrategyBridge
import com.pantham.nexus.task.learning.TaskExecutionLearningBridge
import com.pantham.nexus.task.model.*
import com.pantham.nexus.task.observation.ExecutionEnvironmentObserver
import com.pantham.nexus.task.recovery.TaskRecoveryEngine
import com.pantham.nexus.task.supervisor.NexusTaskSupervisor
import com.pantham.nexus.task.verification.RealWorldExecutionVerifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NexusAutonomousTaskIntelligenceTests {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var verifier: RealWorldExecutionVerifier
    private lateinit var observer: ExecutionEnvironmentObserver
    private lateinit var decisionBridge: TaskDecisionStrategyBridge
    private lateinit var recoveryEngine: TaskRecoveryEngine
    private lateinit var learningBridge: TaskExecutionLearningBridge

    @Before
    fun setup() {
        verifier = RealWorldExecutionVerifier()
        observer = ExecutionEnvironmentObserver()
        decisionBridge = TaskDecisionStrategyBridge(
            decisionEngine = NexusDecisionEngine(),
            learningRuntime = NexusAdaptiveLearningRuntime.getInstance()
        )
        recoveryEngine = TaskRecoveryEngine(decisionBridge)
        learningBridge = TaskExecutionLearningBridge()
    }

    @Test
    fun testRealWorldVerifier_validOutputSucceeds() = runBlocking {
        val node = TaskNode(
            nodeId = "step_1",
            title = "Analyze Data",
            agent = AgentType.RESEARCH
        )
        val result = TaskStepResult(
            nodeId = "step_1",
            success = true,
            message = "Analysis complete with 4 key trends.",
            outputData = mapOf("trends" to listOf("AI", "Autonomous", "Edge"))
        )

        val verification = verifier.verifyStep(node, result)
        assertTrue(verification.verified)
        assertTrue(verification.reason.contains("confirmed", ignoreCase = true))
    }

    @Test
    fun testRealWorldVerifier_emptyOutputFails() = runBlocking {
        val node = TaskNode(
            nodeId = "step_empty",
            title = "Run Worker",
            agent = AgentType.SYSTEM
        )
        val result = TaskStepResult(
            nodeId = "step_empty",
            success = true,
            message = "",
            outputData = emptyMap()
        )

        val verification = verifier.verifyStep(node, result)
        assertFalse(verification.verified)
        assertTrue(verification.reason.contains("empty", ignoreCase = true) || verification.reason.contains("without producing", ignoreCase = true))
    }

    @Test
    fun testRealWorldVerifier_artifactFileCheck() = runBlocking {
        val testFile = tempFolder.newFile("sample_report.pdf")
        testFile.writeText("PDF-1.4 Header Content with verified real bytes.")

        val node = TaskNode(
            nodeId = "step_pdf",
            title = "Generate PDF Report",
            agent = AgentType.FILE
        )
        val artifact = TaskArtifact(
            artifactId = "art_1",
            name = "sample_report.pdf",
            type = ArtifactType.PDF,
            uri = testFile.absolutePath,
            description = "Generated PDF report",
            taskId = "task_pdf_001"
        )
        val result = TaskStepResult(
            nodeId = "step_pdf",
            success = true,
            message = "PDF generated at ${testFile.absolutePath}",
            artifacts = listOf(artifact),
            outputData = mapOf("pdf_path" to testFile.absolutePath)
        )

        val rule = StepVerificationRule(
            ruleId = "rule_artifact_exists",
            type = VerificationType.ARTIFACT_EXISTS,
            description = "Verify physical PDF existence on storage"
        )

        val verification = verifier.verifyStep(node, result, rule)
        assertTrue(verification.verified)
        assertTrue(verification.reason.contains("artifact", ignoreCase = true))
    }

    @Test
    fun testRealWorldVerifier_missingArtifactFails() = runBlocking {
        val node = TaskNode(
            nodeId = "step_pdf_missing",
            title = "Generate PDF Report",
            agent = AgentType.FILE
        )
        val nonExistentPath = File(tempFolder.root, "ghost_file.pdf").absolutePath
        val artifact = TaskArtifact(
            artifactId = "art_ghost",
            name = "ghost_file.pdf",
            type = ArtifactType.PDF,
            uri = nonExistentPath,
            description = "Missing PDF report",
            taskId = "task_pdf_ghost"
        )
        val result = TaskStepResult(
            nodeId = "step_pdf_missing",
            success = true,
            message = "Report saved",
            artifacts = listOf(artifact)
        )

        val verification = verifier.verifyStep(node, result)
        assertFalse(verification.verified)
        assertTrue(verification.reason.contains("not found", ignoreCase = true))
    }

    @Test
    fun testObserver_evaluatesCriticalNetworkLoss() {
        val initial = TaskObservationSnapshot(
            networkAvailable = true,
            isWifi = true,
            batteryPercent = 80
        )
        val offline = TaskObservationSnapshot(
            networkAvailable = false,
            isWifi = false,
            batteryPercent = 79
        )

        val eval = observer.evaluateContextChange(
            initialSnapshot = initial,
            currentSnapshot = offline,
            stepRequiresNetwork = true
        )

        assertEquals(ContextChangeSeverity.CRITICAL_REPLAN_NEEDED, eval.severity)
        assertTrue(eval.requiresPauseOrReplan)
        assertTrue(eval.reason.contains("connectivity lost", ignoreCase = true))
    }

    @Test
    fun testObserver_evaluatesDrivingContextChange() {
        val initial = TaskObservationSnapshot(
            isUserDriving = false
        )
        val driving = TaskObservationSnapshot(
            isUserDriving = true
        )

        val eval = observer.evaluateContextChange(
            initialSnapshot = initial,
            currentSnapshot = driving,
            stepRequiresScreen = true
        )

        assertEquals(ContextChangeSeverity.CRITICAL_REPLAN_NEEDED, eval.severity)
        assertTrue(eval.requiresPauseOrReplan)
        assertTrue(eval.reason.contains("driving", ignoreCase = true))
    }

    @Test
    fun testRecoveryEngine_selectsRetryWithBackoff() = runBlocking {
        val node = TaskNode(
            nodeId = "node_retry",
            title = "Fetch API Data",
            agent = AgentType.RESEARCH
        )
        val failedResult = TaskStepResult(
            nodeId = "node_retry",
            success = false,
            message = "Transient connection timeout"
        )

        val decision = recoveryEngine.handleStepFailure(
            node = node,
            stepResult = failedResult,
            verificationResult = null,
            currentRetryCount = 0
        )

        assertNotNull(decision)
        assertTrue(
            decision.strategy == RecoveryStrategy.RETRY_WITH_BACKOFF ||
                    decision.strategy == RecoveryStrategy.FALLBACK_ALTERNATIVE_STEP
        )
    }

    @Test
    fun testRecoveryEngine_asksUserWhenExplicitlyRequested() = runBlocking {
        val node = TaskNode(
            nodeId = "node_clarify",
            title = "Send Confidential Email",
            agent = AgentType.COMMUNICATION
        )
        val clarificationResult = TaskStepResult(
            nodeId = "node_clarify",
            success = false,
            message = "Recipient has multiple emails",
            requiresUserClarification = true,
            clarificationPrompt = "Which email address should I send to: work or personal?"
        )

        val decision = recoveryEngine.handleStepFailure(
            node = node,
            stepResult = clarificationResult,
            verificationResult = null,
            currentRetryCount = 0
        )

        assertEquals(RecoveryStrategy.ASK_USER, decision.strategy)
        assertEquals("Which email address should I send to: work or personal?", decision.clarificationPrompt)
    }

    @Test
    fun testSupervisor_executesAndVerifiesMultiStepDAG() = runBlocking {
        val updatedTasks = mutableListOf<TaskItem>()

        val supervisor = NexusTaskSupervisor(
            actionRouter = null,
            checkpointStore = null,
            artifactRegistry = null,
            onTaskUpdated = { updatedTasks.add(it) },
            verifier = verifier,
            observer = observer,
            recoveryEngine = recoveryEngine,
            learningBridge = learningBridge
        )

        val step1 = TaskNode(
            nodeId = "step_1",
            title = "Step 1: Gather Metrics",
            agent = AgentType.RESEARCH,
            customExecution = { _ ->
                TaskStepResult(
                    nodeId = "step_1",
                    success = true,
                    message = "Metrics gathered successfully",
                    outputData = mapOf("metric_val" to 42)
                )
            }
        )

        val step2 = TaskNode(
            nodeId = "step_2",
            title = "Step 2: Summarize Metrics",
            agent = AgentType.PLANNER,
            dependencies = setOf("step_1"),
            customExecution = { outputs ->
                val metric = outputs["metric_val"] ?: 0
                TaskStepResult(
                    nodeId = "step_2",
                    success = true,
                    message = "Summary completed for metric: $metric",
                    outputData = mapOf("summary" to "Verified metric $metric")
                )
            }
        )

        val goal = GoalDefinition(
            goalId = "GOAL_TEST_001",
            userGoal = "Generate verified metrics summary",
            priority = GoalPriority.HIGH
        )

        val report = supervisor.superviseTask(
            goal = goal,
            nodes = listOf(step1, step2)
        )

        assertEquals(TaskExecutionStatus.COMPLETED, report.status)
        assertEquals(2, report.totalSteps)
        assertEquals(2, report.completedStepsCount)
        assertTrue(report.summary.contains("completed successfully", ignoreCase = true))
        assertTrue(report.timeline.any { it.stage == "STEP_VERIFIED" })
    }

    @Test
    fun testSupervisor_pausesGracefullyWhenInterrupted() = runBlocking {
        val supervisor = NexusTaskSupervisor(
            actionRouter = null,
            checkpointStore = null,
            artifactRegistry = null,
            onTaskUpdated = {},
            verifier = verifier,
            observer = observer
        )

        val pauseMsg = supervisor.pauseActiveTask("GOAL_PAUSE_001")
        assertTrue(pauseMsg.contains("paused safely", ignoreCase = true))
    }
}
