package com.pantham.nexus.situational.signal

import com.pantham.nexus.situational.model.*

class DefaultDeviceSituationProvider : DeviceSituationProvider {
    override suspend fun getDeviceSituation(): DeviceSituation {
        return DeviceSituation()
    }
}

class DefaultAppSituationProvider : AppSituationProvider {
    override suspend fun getAppSituation(): AppSituation {
        return AppSituation()
    }
}

class DefaultNetworkSituationProvider : NetworkSituationProvider {
    override suspend fun getNetworkSituation(): NetworkSituation {
        return NetworkSituation()
    }
}

class DefaultSessionSituationProvider : SessionSituationProvider {
    override suspend fun getSessionSituation(): SessionSituation {
        return SessionSituation()
    }
}

class DefaultNotificationSituationProvider : NotificationSituationProvider {
    override suspend fun getNotificationSituation(): NotificationSituation {
        return NotificationSituation()
    }
}

class DefaultActivitySituationProvider : ActivitySituationProvider {
    override suspend fun getActivitySituation(): ActivitySituation {
        return ActivitySituation()
    }
}

class DefaultGoalSituationProvider : GoalSituationProvider {
    override suspend fun getGoalSituation(): GoalSituation {
        return GoalSituation()
    }
}

class DefaultActionSituationProvider : ActionSituationProvider {
    override suspend fun getActionSituation(): ActionSituation {
        return ActionSituation()
    }
}

class DefaultPredictionSituationProvider : PredictionSituationProvider {
    override suspend fun getPredictionSituation(): PredictionSituation {
        return PredictionSituation()
    }
}

class DefaultVisionSituationProvider : VisionSituationProvider {
    override suspend fun getVisionSituation(): VisionSituation {
        return VisionSituation()
    }
}

class DefaultTemporalSituationProvider : TemporalSituationProvider {
    override suspend fun getTemporalSituation(): TemporalSituation {
        return TemporalSituation()
    }
}
