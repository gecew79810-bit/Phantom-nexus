package com.pantham.nexus.situational.runtime

import com.pantham.nexus.situational.controller.NexusSituationController
import com.pantham.nexus.situational.engine.NexusSituationalAwarenessEngine
import com.pantham.nexus.situational.health.NexusSituationHealthManager
import com.pantham.nexus.situational.signal.*

object NexusSituationRuntime {

    lateinit var engine: NexusSituationalAwarenessEngine
        private set

    lateinit var controller: NexusSituationController
        private set

    lateinit var health: NexusSituationHealthManager
        private set

    fun initialize(
        deviceProvider: DeviceSituationProvider =
            DefaultDeviceSituationProvider(),

        appProvider: AppSituationProvider =
            DefaultAppSituationProvider(),

        networkProvider: NetworkSituationProvider =
            DefaultNetworkSituationProvider(),

        sessionProvider: SessionSituationProvider =
            DefaultSessionSituationProvider(),

        notificationProvider: NotificationSituationProvider =
            DefaultNotificationSituationProvider(),

        activityProvider: ActivitySituationProvider =
            DefaultActivitySituationProvider(),

        goalProvider: GoalSituationProvider =
            DefaultGoalSituationProvider(),

        actionProvider: ActionSituationProvider =
            DefaultActionSituationProvider(),

        predictionProvider: PredictionSituationProvider =
            DefaultPredictionSituationProvider(),

        visionProvider: VisionSituationProvider =
            DefaultVisionSituationProvider(),

        temporalProvider: TemporalSituationProvider =
            DefaultTemporalSituationProvider()
    ) {

        health = NexusSituationHealthManager()

        engine = NexusSituationalAwarenessEngine(
            deviceProvider = deviceProvider,
            appProvider = appProvider,
            networkProvider = networkProvider,
            sessionProvider = sessionProvider,
            notificationProvider = notificationProvider,
            activityProvider = activityProvider,
            goalProvider = goalProvider,
            actionProvider = actionProvider,
            predictionProvider = predictionProvider,
            visionProvider = visionProvider,
            temporalProvider = temporalProvider
        )

        controller = NexusSituationController(
            engine = engine
        )
    }
}
