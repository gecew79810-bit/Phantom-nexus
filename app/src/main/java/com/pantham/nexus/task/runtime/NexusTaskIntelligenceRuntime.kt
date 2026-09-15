package com.pantham.nexus.task.runtime

import com.example.action.NexusActionRouter
import com.example.action.goal.ArtifactRegistry
import com.example.action.goal.AutonomousGoalPlanner
import com.example.action.goal.TaskCheckpointStore
import com.example.ai.TaskItem
import com.pantham.nexus.task.controller.AutonomousTaskController
import com.pantham.nexus.task.learning.TaskExecutionLearningBridge
import com.pantham.nexus.task.observation.ExecutionEnvironmentObserver
import com.pantham.nexus.task.recovery.TaskRecoveryEngine
import com.pantham.nexus.task.supervisor.NexusTaskSupervisor
import com.pantham.nexus.task.verification.RealWorldExecutionVerifier

/**
 * Singleton Runtime for Autonomous Task Intelligence / Execution Intelligence V1.
 */
object NexusTaskIntelligenceRuntime {

    lateinit var supervisor: NexusTaskSupervisor
        private set

    lateinit var controller: AutonomousTaskController
        private set

    lateinit var verifier: RealWorldExecutionVerifier
        private set

    lateinit var observer: ExecutionEnvironmentObserver
        private set

    lateinit var recoveryEngine: TaskRecoveryEngine
        private set

    @Volatile
    private var isInitialized = false

    fun isReady(): Boolean = isInitialized

    fun initialize(
        actionRouter: NexusActionRouter,
        checkpointStore: TaskCheckpointStore,
        artifactRegistry: ArtifactRegistry,
        goalPlanner: AutonomousGoalPlanner,
        onTaskUpdated: (TaskItem) -> Unit,
        customVerifier: RealWorldExecutionVerifier? = null,
        customObserver: ExecutionEnvironmentObserver? = null,
        customRecoveryEngine: TaskRecoveryEngine? = null,
        customLearningBridge: TaskExecutionLearningBridge? = null
    ) {
        verifier = customVerifier ?: RealWorldExecutionVerifier()
        observer = customObserver ?: ExecutionEnvironmentObserver()
        recoveryEngine = customRecoveryEngine ?: TaskRecoveryEngine()
        val learningBridge = customLearningBridge ?: TaskExecutionLearningBridge()

        supervisor = NexusTaskSupervisor(
            actionRouter = actionRouter,
            checkpointStore = checkpointStore,
            artifactRegistry = artifactRegistry,
            onTaskUpdated = onTaskUpdated,
            verifier = verifier,
            observer = observer,
            recoveryEngine = recoveryEngine,
            learningBridge = learningBridge
        )

        controller = AutonomousTaskController(
            supervisor = supervisor,
            goalPlanner = goalPlanner,
            checkpointStore = checkpointStore
        )

        isInitialized = true
    }

    /**
     * Resets runtime for testing purposes.
     */
    fun resetForTesting() {
        isInitialized = false
    }
}
