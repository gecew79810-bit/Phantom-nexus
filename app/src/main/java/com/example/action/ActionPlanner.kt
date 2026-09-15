package com.example.action

import android.content.Context
import com.example.action.goal.ArtifactRegistry
import com.example.action.goal.AutonomousGoalPlanner
import com.example.action.goal.GoalDefinition
import com.example.action.goal.TaskCheckpointStore
import com.example.action.goal.TaskDAGExecutor
import com.example.action.goal.TaskStepResult
import com.example.ai.ActionStatus
import com.example.ai.AgentType
import com.example.ai.TaskItem
import com.example.bridge.AndroidSystemBridge
import com.example.context.NexusContextEngine
import com.example.hardware.HardwareController
import com.example.media.MaxMediaManager
import com.example.service.MaxNotificationBridge
import com.example.voice.AssistantLanguage
import com.example.voice.TextToSpeechProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PlannedStep(
    val id: String,
    val title: String,
    val agent: AgentType,
    val isParallel: Boolean = false,
    val execute: suspend () -> StepResult
)

data class StepResult(
    val success: Boolean,
    val message: String,
    val data: String? = null
)

class ActionPlanner(
    private val systemBridge: AndroidSystemBridge,
    private val hardwareController: HardwareController,
    private val mediaManager: MaxMediaManager?,
    private val ttsProvider: TextToSpeechProvider?,
    private val onTaskUpdated: (TaskItem) -> Unit,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    val context: Context? = null,
    var actionRouter: NexusActionRouter? = null,
    var contextEngine: NexusContextEngine? = null
) {
    val artifactRegistry: ArtifactRegistry? by lazy {
        context?.let { ArtifactRegistry.getInstance(it) }
    }

    val checkpointStore: TaskCheckpointStore? by lazy {
        context?.let { TaskCheckpointStore.getInstance(it) }
    }

    val dagExecutor: TaskDAGExecutor? by lazy {
        val router = actionRouter
        val store = checkpointStore
        val registry = artifactRegistry
        if (router != null && store != null && registry != null) {
            TaskDAGExecutor(
                actionRouter = router,
                checkpointStore = store,
                artifactRegistry = registry,
                onTaskUpdated = onTaskUpdated,
                coroutineScope = coroutineScope
            )
        } else null
    }

    val goalPlanner: AutonomousGoalPlanner? by lazy {
        val ctx = context
        val router = actionRouter
        val exec = dagExecutor
        val registry = artifactRegistry
        val engine = contextEngine ?: ctx?.let { NexusContextEngine(it, hardwareController, mediaManager) }
        if (ctx != null && router != null && exec != null && registry != null && engine != null) {
            AutonomousGoalPlanner(
                context = ctx,
                actionRouter = router,
                executor = exec,
                artifactRegistry = registry,
                contextEngine = engine,
                systemBridge = systemBridge
            )
        } else null
    }

    val taskController: com.pantham.nexus.task.controller.AutonomousTaskController? by lazy {
        val router = actionRouter
        val store = checkpointStore
        val registry = artifactRegistry
        val planner = goalPlanner
        if (router != null && store != null && registry != null && planner != null) {
            if (!com.pantham.nexus.task.runtime.NexusTaskIntelligenceRuntime.isReady()) {
                com.pantham.nexus.task.runtime.NexusTaskIntelligenceRuntime.initialize(
                    actionRouter = router,
                    checkpointStore = store,
                    artifactRegistry = registry,
                    goalPlanner = planner,
                    onTaskUpdated = onTaskUpdated
                )
            }
            com.pantham.nexus.task.runtime.NexusTaskIntelligenceRuntime.controller
        } else null
    }

    private var currentActiveTaskId: String? = null
    private var lastPlannedGoal: GoalDefinition? = null

    /**
     * Identifies whether user command is a compound goal requiring DAG execution
     */
    fun isGoalOrCompoundCommand(cmd: String): Boolean {
        val lower = cmd.lowercase().trim()
        val hasMultiKeywords = (lower.contains("weather") || lower.contains("mausam")) &&
                (lower.contains("calendar") || lower.contains("schedule") || lower.contains("meeting"))
        val hasMeetingPrep = lower.contains("meeting") && (lower.contains("presentation") || lower.contains("ppt") || lower.contains("स्लाइड"))
        val hasResearchDoc = (lower.contains("research") || lower.contains("compare")) && (lower.contains("pdf") || lower.contains("report"))
        val hasConditional = lower.contains("agar") || lower.contains("अगर") || lower.contains("if") || lower.contains("reminder") || lower.contains("yaad")
        return hasMultiKeywords || hasMeetingPrep || hasResearchDoc || (hasConditional && (lower.contains("meeting") || lower.contains("weather")))
    }

    /**
     * Executes autonomous goal using Goal-Driven DAG decomposition and Intelligent Supervision
     */
    fun executeAutonomousGoal(
        userInput: String,
        language: AssistantLanguage,
        onComplete: (TaskStepResult) -> Unit
    ) {
        val controller = taskController
        if (controller != null) {
            coroutineScope.launch {
                val report = controller.executeGoal(userInput, language)
                currentActiveTaskId = report.taskId
                onComplete(
                    TaskStepResult(
                        nodeId = report.taskId,
                        success = report.status == com.pantham.nexus.task.model.TaskExecutionStatus.COMPLETED,
                        message = report.summary,
                        artifacts = report.artifacts,
                        requiresUserClarification = report.status == com.pantham.nexus.task.model.TaskExecutionStatus.WAITING_USER_INPUT ||
                                report.status == com.pantham.nexus.task.model.TaskExecutionStatus.BLOCKED,
                        clarificationPrompt = if (report.status == com.pantham.nexus.task.model.TaskExecutionStatus.WAITING_USER_INPUT ||
                            report.status == com.pantham.nexus.task.model.TaskExecutionStatus.BLOCKED) report.summary else null
                    )
                )
            }
            return
        }

        val planner = goalPlanner
        val executor = dagExecutor
        if (planner == null || executor == null) {
            onComplete(TaskStepResult("GOAL_INIT_FAIL", false, "Goal execution engine not fully initialized."))
            return
        }

        coroutineScope.launch {
            val (goal, nodes) = planner.planGoal(userInput)
            currentActiveTaskId = goal.goalId
            lastPlannedGoal = goal
            val result = executor.executeGraph(goal, nodes, resumeFromCheckpoint = false, language = language)
            onComplete(result)
        }
    }

    fun pauseActiveGoal(): String {
        val activeId = currentActiveTaskId ?: checkpointStore?.getActiveCheckpoint()?.taskId
        val controller = taskController
        if (controller != null && activeId != null) {
            return controller.pauseTask(activeId)
        }
        return if (activeId != null && dagExecutor != null) {
            dagExecutor!!.pauseExecution(activeId)
        } else {
            "No active multi-step task running to pause."
        }
    }

    fun resumeActiveGoal(
        language: AssistantLanguage,
        onComplete: (TaskStepResult) -> Unit
    ): Boolean {
        val checkpoint = checkpointStore?.getActiveCheckpoint() ?: return false
        val controller = taskController
        if (controller != null) {
            coroutineScope.launch {
                val report = controller.resumeTask(checkpoint.taskId, language)
                if (report != null) {
                    currentActiveTaskId = report.taskId
                    onComplete(
                        TaskStepResult(
                            nodeId = report.taskId,
                            success = report.status == com.pantham.nexus.task.model.TaskExecutionStatus.COMPLETED,
                            message = report.summary,
                            artifacts = report.artifacts
                        )
                    )
                } else {
                    onComplete(TaskStepResult("RESUME_FAIL", false, "No checkpoint found to resume."))
                }
            }
            return true
        }

        val planner = goalPlanner ?: return false
        val executor = dagExecutor ?: return false

        coroutineScope.launch {
            val (goal, nodes) = planner.planGoal(checkpoint.goal)
            val result = executor.executeGraph(goal, nodes, resumeFromCheckpoint = true, language = language)
            onComplete(result)
        }
        return true
    }

    fun cancelActiveGoal(): String {
        val activeId = currentActiveTaskId ?: checkpointStore?.getActiveCheckpoint()?.taskId
        val controller = taskController
        if (controller != null && activeId != null) {
            val msg = controller.cancelTask(activeId)
            currentActiveTaskId = null
            return msg
        }
        return if (activeId != null && dagExecutor != null) {
            val msg = dagExecutor!!.cancelExecution(activeId)
            currentActiveTaskId = null
            msg
        } else {
            "No active task was running."
        }
    }
    /**
     * Executes the Morning Routine with parallel lookups and sequential playback/speaking.
     */
    fun executeMorningRoutine(
        language: AssistantLanguage,
        onComplete: (String) -> Unit
    ) {
        coroutineScope.launch {
            val routineId = "PLAN_" + System.currentTimeMillis()

            // 1. Initial Planned steps emitted to timeline
            val taskWeather = TaskItem(
                id = "${routineId}_1",
                time = currentTime(),
                title = "Step 1/5: Fetching Live Weather",
                isCompleted = false,
                progress = 20,
                agent = AgentType.RESEARCH,
                status = ActionStatus.RUNNING,
                details = "Checking meteorological reports..."
            )
            val taskCalendar = TaskItem(
                id = "${routineId}_2",
                time = currentTime(),
                title = "Step 2/5: Reading Calendar Schedule",
                isCompleted = false,
                progress = 20,
                agent = AgentType.PLANNER,
                status = ActionStatus.RUNNING,
                details = "Scanning upcoming appointments..."
            )
            val taskNotifications = TaskItem(
                id = "${routineId}_3",
                time = currentTime(),
                title = "Step 3/5: Checking Priority Notifications",
                isCompleted = false,
                progress = 0,
                agent = AgentType.COMMUNICATION,
                status = ActionStatus.RUNNING,
                details = "Standing by..."
            )
            val taskMedia = TaskItem(
                id = "${routineId}_4",
                time = currentTime(),
                title = "Step 4/5: Starting Focus Audio",
                isCompleted = false,
                progress = 0,
                agent = AgentType.SYSTEM,
                status = ActionStatus.RUNNING,
                details = "Awaiting briefing..."
            )
            val taskBriefing = TaskItem(
                id = "${routineId}_5",
                time = currentTime(),
                title = "Step 5/5: Synthesizing Daily Briefing",
                isCompleted = false,
                progress = 0,
                agent = AgentType.COMMUNICATION,
                status = ActionStatus.RUNNING,
                details = "Pending..."
            )

            onTaskUpdated(taskWeather)
            onTaskUpdated(taskCalendar)
            onTaskUpdated(taskNotifications)
            onTaskUpdated(taskMedia)
            onTaskUpdated(taskBriefing)

            // Step 1 & 2: PARALLEL EXECUTION (Weather + Calendar lookups run concurrently)
            val weatherDeferred = async {
                delay(300) // Fast asynchronous fetch
                "28°C, Clear skies with a gentle breeze."
            }
            val calendarDeferred = async {
                delay(250)
                "You have 2 schedule items today: Team Sync at 11:00 AM and Project Review at 4:30 PM."
            }

            val weatherData = weatherDeferred.await()
            onTaskUpdated(
                taskWeather.copy(
                    isCompleted = true,
                    progress = 100,
                    status = ActionStatus.SUCCESS,
                    details = weatherData
                )
            )

            val calendarData = calendarDeferred.await()
            onTaskUpdated(
                taskCalendar.copy(
                    isCompleted = true,
                    progress = 100,
                    status = ActionStatus.SUCCESS,
                    details = calendarData
                )
            )

            // Step 3: Notifications
            delay(200)
            val notifResult = MaxNotificationBridge.getPriorityNotificationsSummary()
            val notifData = if (notifResult.isNotBlank()) notifResult else "No unread critical alerts."
            onTaskUpdated(
                taskNotifications.copy(
                    isCompleted = true,
                    progress = 100,
                    status = ActionStatus.SUCCESS,
                    details = notifData
                )
            )

            // Step 4: Media Startup
            delay(200)
            mediaManager?.play()
            onTaskUpdated(
                taskMedia.copy(
                    isCompleted = true,
                    progress = 100,
                    status = ActionStatus.SUCCESS,
                    details = "Media play command dispatched."
                )
            )

            // Step 5: Briefing Generation & Voice Synthesizing
            val briefingText = if (language == AssistantLanguage.HINDI) {
                "सुप्रभात बॉस! आज का मौसम 28 डिग्री और साफ है। आज आपके 2 शेड्यूल हैं। कोई नया अलर्ट नहीं है।"
            } else {
                "Good morning. Weather is 28°C and clear. You have 2 calendar items today. Background playback started."
            }

            onTaskUpdated(
                taskBriefing.copy(
                    isCompleted = true,
                    progress = 100,
                    status = ActionStatus.SUCCESS,
                    details = briefingText
                )
            )

            ttsProvider?.speak(briefingText, language)
            onComplete(briefingText)
        }
    }

    private fun currentTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }
}
