package com.pantham.nexus.situational.engine

import com.pantham.nexus.situational.model.*
import com.pantham.nexus.situational.signal.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class NexusSituationalAwarenessEngine(
    private val deviceProvider: DeviceSituationProvider,
    private val appProvider: AppSituationProvider,
    private val networkProvider: NetworkSituationProvider,
    private val sessionProvider: SessionSituationProvider,
    private val notificationProvider: NotificationSituationProvider,
    private val activityProvider: ActivitySituationProvider,
    private val goalProvider: GoalSituationProvider,
    private val actionProvider: ActionSituationProvider,
    private val predictionProvider: PredictionSituationProvider,
    private val visionProvider: VisionSituationProvider,
    private val temporalProvider: TemporalSituationProvider,

    private val conflictEngine: NexusSituationConflictEngine =
        NexusSituationConflictEngine(),

    private val activityEngine: NexusSituationActivityEngine =
        NexusSituationActivityEngine(),

    private val priorityEngine: NexusSituationPriorityEngine =
        NexusSituationPriorityEngine(),

    private val focusEngine: NexusSituationFocusEngine =
        NexusSituationFocusEngine(),

    private val interruptionEngine: NexusSituationInterruptionEngine =
        NexusSituationInterruptionEngine(),

    private val summaryEngine: NexusSituationSummaryEngine =
        NexusSituationSummaryEngine(),

    private val changeEngine: NexusSituationChangeEngine =
        NexusSituationChangeEngine()
) {

    private var lastContext: SituationalContext? = null

    suspend fun buildContext(): SituationalContext = coroutineScope {

        val device = async { safeDevice() }
        val app = async { safeApp() }
        val network = async { safeNetwork() }
        val session = async { safeSession() }
        val notifications = async { safeNotifications() }
        val activity = async { safeActivity() }
        val goal = async { safeGoal() }
        val action = async { safeAction() }
        val prediction = async { safePrediction() }
        val vision = async { safeVision() }
        val temporal = async { safeTemporal() }

        val deviceState = device.await()
        val appState = app.await()
        val networkState = network.await()
        val sessionState = session.await()
        val notificationState = notifications.await()
        val goalState = goal.await()
        val actionState = action.await()
        val predictionState = prediction.await()
        val visionState = vision.await()
        val temporalState = temporal.await()

        val inferredActivity = try {
            val providerActivity = activity.await()

            if (providerActivity.confidence > 0f) {
                providerActivity
            } else {
                activityEngine.infer(
                    app = appState,
                    session = sessionState,
                    vision = visionState,
                    goal = goalState
                )
            }

        } catch (_: Throwable) {
            activityEngine.infer(
                app = appState,
                session = sessionState,
                vision = visionState,
                goal = goalState
            )
        }

        val conflicts = conflictEngine.detect(
            device = deviceState,
            app = appState,
            network = networkState,
            session = sessionState,
            goal = goalState,
            action = actionState,
            temporal = temporalState
        )

        val priority = priorityEngine.calculate(
            device = deviceState,
            app = appState,
            network = networkState,
            session = sessionState,
            notifications = notificationState,
            goal = goalState,
            action = actionState,
            prediction = predictionState,
            temporal = temporalState,
            conflicts = conflicts
        )

        val focus = focusEngine.findFocus(
            notifications = notificationState,
            goal = goalState,
            action = actionState,
            temporal = temporalState,
            conflicts = conflicts,
            app = appState
        )

        val interruption = interruptionEngine.evaluate(
            session = sessionState,
            notifications = notificationState,
            temporal = temporalState,
            action = actionState,
            conflicts = conflicts
        )

        val activeState = when {
            actionState.pending -> SituationState.ACTIVE
            goalState.blocked -> SituationState.BLOCKED
            goalState.waitingForUser -> SituationState.WAITING
            sessionState.active -> SituationState.ACTIVE
            appState.foreground -> SituationState.ACTIVE
            else -> SituationState.IDLE
        }

        var context = SituationalContext(
            device = deviceState,
            app = appState,
            network = networkState,
            session = sessionState,
            notifications = notificationState,

            activity = inferredActivity,

            goal = goalState,
            action = actionState,
            prediction = predictionState,
            vision = visionState,
            temporal = temporalState,

            activeState = activeState,
            interruptionLevel = interruption,
            priority = priority,

            focus = focus,

            conflicts = conflicts
        )

        val change = changeEngine.detect(
            previous = lastContext,
            current = context
        )

        context = context.copy(
            significantChange = change.significance >= 0.55f,
            changeType = change.type,
            summary = summaryEngine.summarize(context)
        )

        lastContext = context

        context
    }

    fun currentContext(): SituationalContext? = lastContext

    fun detectCurrentChange(): SituationChangeEvent? {
        val current = lastContext ?: return null

        return changeEngine.detect(
            previous = lastContext,
            current = current
        )
    }

    private suspend fun safeDevice(): DeviceSituation =
        try {
            deviceProvider.getDeviceSituation()
        } catch (_: Throwable) {
            DeviceSituation()
        }

    private suspend fun safeApp(): AppSituation =
        try {
            appProvider.getAppSituation()
        } catch (_: Throwable) {
            AppSituation()
        }

    private suspend fun safeNetwork(): NetworkSituation =
        try {
            networkProvider.getNetworkSituation()
        } catch (_: Throwable) {
            NetworkSituation()
        }

    private suspend fun safeSession(): SessionSituation =
        try {
            sessionProvider.getSessionSituation()
        } catch (_: Throwable) {
            SessionSituation()
        }

    private suspend fun safeNotifications(): NotificationSituation =
        try {
            notificationProvider.getNotificationSituation()
        } catch (_: Throwable) {
            NotificationSituation()
        }

    private suspend fun safeActivity(): ActivitySituation =
        try {
            activityProvider.getActivitySituation()
        } catch (_: Throwable) {
            ActivitySituation()
        }

    private suspend fun safeGoal(): GoalSituation =
        try {
            goalProvider.getGoalSituation()
        } catch (_: Throwable) {
            GoalSituation()
        }

    private suspend fun safeAction(): ActionSituation =
        try {
            actionProvider.getActionSituation()
        } catch (_: Throwable) {
            ActionSituation()
        }

    private suspend fun safePrediction(): PredictionSituation =
        try {
            predictionProvider.getPredictionSituation()
        } catch (_: Throwable) {
            PredictionSituation()
        }

    private suspend fun safeVision(): VisionSituation =
        try {
            visionProvider.getVisionSituation()
        } catch (_: Throwable) {
            VisionSituation()
        }

    private suspend fun safeTemporal(): TemporalSituation =
        try {
            temporalProvider.getTemporalSituation()
        } catch (_: Throwable) {
            TemporalSituation()
        }
}
