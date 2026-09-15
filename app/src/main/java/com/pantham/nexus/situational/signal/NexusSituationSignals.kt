package com.pantham.nexus.situational.signal

import com.pantham.nexus.situational.model.*

interface DeviceSituationProvider {
    suspend fun getDeviceSituation(): DeviceSituation
}

interface AppSituationProvider {
    suspend fun getAppSituation(): AppSituation
}

interface NetworkSituationProvider {
    suspend fun getNetworkSituation(): NetworkSituation
}

interface SessionSituationProvider {
    suspend fun getSessionSituation(): SessionSituation
}

interface NotificationSituationProvider {
    suspend fun getNotificationSituation(): NotificationSituation
}

interface ActivitySituationProvider {
    suspend fun getActivitySituation(): ActivitySituation
}

interface GoalSituationProvider {
    suspend fun getGoalSituation(): GoalSituation
}

interface ActionSituationProvider {
    suspend fun getActionSituation(): ActionSituation
}

interface PredictionSituationProvider {
    suspend fun getPredictionSituation(): PredictionSituation
}

interface VisionSituationProvider {
    suspend fun getVisionSituation(): VisionSituation
}

interface TemporalSituationProvider {
    suspend fun getTemporalSituation(): TemporalSituation
}
