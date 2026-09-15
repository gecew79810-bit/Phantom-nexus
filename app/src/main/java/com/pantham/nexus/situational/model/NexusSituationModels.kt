package com.pantham.nexus.situational.model

import java.time.Instant

enum class SituationSource {
    DEVICE,
    APP,
    NETWORK,
    SESSION,
    NOTIFICATION,
    CONVERSATION,
    INTENT,
    GOAL,
    ACTION,
    PREDICTION,
    KNOWLEDGE,
    VISION,
    TEMPORAL,
    CALENDAR,
    USER_ACTIVITY,
    SYSTEM
}

enum class SituationState {
    ACTIVE,
    IDLE,
    BLOCKED,
    WAITING,
    INTERRUPTED,
    UNKNOWN
}

enum class SituationPriority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    BACKGROUND
}

enum class ActivityMode {
    UNKNOWN,
    IDLE,
    READING,
    TYPING,
    SPEAKING,
    LISTENING,
    WATCHING,
    CALLING,
    NAVIGATING,
    WORKING,
    ENTERTAINMENT,
    SHOPPING,
    TRAVELING,
    MEETING,
    FILE_OPERATION,
    SYSTEM_OPERATION
}

enum class InterruptionLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class SituationChangeType {
    NONE,
    APP_CHANGED,
    NETWORK_CHANGED,
    SESSION_CHANGED,
    NOTIFICATION_CHANGED,
    GOAL_CHANGED,
    ACTION_CHANGED,
    ACTIVITY_CHANGED,
    VISION_CHANGED,
    TEMPORAL_CHANGED,
    MAJOR_CONTEXT_CHANGE
}

data class DeviceSituation(
    val screenOn: Boolean = true,
    val charging: Boolean = false,
    val batteryPercent: Int = -1,
    val batteryLow: Boolean = false,
    val airplaneMode: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val wifiConnected: Boolean = false,
    val mobileDataConnected: Boolean = false,
    val doNotDisturb: Boolean = false
)

data class AppSituation(
    val packageName: String? = null,
    val appName: String? = null,
    val category: String? = null,
    val foreground: Boolean = false,
    val elapsedMs: Long = 0L
)

data class NetworkSituation(
    val connected: Boolean = false,
    val transport: String? = null,
    val metered: Boolean = false,
    val validated: Boolean = false,
    val latencyMs: Long? = null
)

data class SessionSituation(
    val active: Boolean = false,
    val listening: Boolean = false,
    val speaking: Boolean = false,
    val voiceMode: Boolean = false,
    val sessionId: String? = null,
    val lastInteractionAt: Long = 0L,
    val interruptionAllowed: Boolean = true
)

data class NotificationSituation(
    val unreadCount: Int = 0,
    val urgentCount: Int = 0,
    val latestPackage: String? = null,
    val hasActionableNotification: Boolean = false
)

data class ActivitySituation(
    val mode: ActivityMode = ActivityMode.UNKNOWN,
    val confidence: Float = 0f,
    val inferredFrom: Set<SituationSource> = emptySet()
)

data class GoalSituation(
    val active: Boolean = false,
    val goalId: String? = null,
    val title: String? = null,
    val progress: Float = 0f,
    val blocked: Boolean = false,
    val waitingForUser: Boolean = false
)

data class ActionSituation(
    val pending: Boolean = false,
    val actionId: String? = null,
    val description: String? = null,
    val requiresConfirmation: Boolean = false,
    val riskLevel: String? = null
)

data class PredictionSituation(
    val strongestPrediction: String? = null,
    val confidence: Float = 0f,
    val predictionCount: Int = 0
)

data class VisionSituation(
    val available: Boolean = false,
    val summary: String? = null,
    val confidence: Float = 0f,
    val objectCount: Int = 0,
    val textDetected: Boolean = false
)

data class TemporalSituation(
    val currentLabel: String? = null,
    val upcomingEvent: String? = null,
    val minutesUntilUpcomingEvent: Long? = null,
    val timeSensitive: Boolean = false
)

data class SituationEvidence(
    val source: SituationSource,
    val signal: String,
    val confidence: Float = 1f,
    val timestamp: Long = System.currentTimeMillis()
)

data class SituationConflict(
    val leftSignal: String,
    val rightSignal: String,
    val severity: Float,
    val explanation: String
)

data class SituationFocus(
    val title: String,
    val reason: String,
    val score: Float,
    val priority: SituationPriority,
    val relatedSources: Set<SituationSource> = emptySet()
)

data class SituationalContext(
    val generatedAt: Long = System.currentTimeMillis(),

    val device: DeviceSituation = DeviceSituation(),
    val app: AppSituation = AppSituation(),
    val network: NetworkSituation = NetworkSituation(),
    val session: SessionSituation = SessionSituation(),
    val notifications: NotificationSituation = NotificationSituation(),

    val activity: ActivitySituation = ActivitySituation(),

    val goal: GoalSituation = GoalSituation(),
    val action: ActionSituation = ActionSituation(),
    val prediction: PredictionSituation = PredictionSituation(),
    val vision: VisionSituation = VisionSituation(),
    val temporal: TemporalSituation = TemporalSituation(),

    val activeState: SituationState = SituationState.UNKNOWN,
    val interruptionLevel: InterruptionLevel = InterruptionLevel.NONE,
    val priority: SituationPriority = SituationPriority.BACKGROUND,

    val focus: SituationFocus? = null,

    val evidence: List<SituationEvidence> = emptyList(),
    val conflicts: List<SituationConflict> = emptyList(),

    val significantChange: Boolean = false,
    val changeType: SituationChangeType = SituationChangeType.NONE,

    val summary: String = ""
)

data class SituationSnapshot(
    val context: SituationalContext,
    val timestamp: Long = System.currentTimeMillis()
)

data class SituationChangeEvent(
    val previous: SituationalContext?,
    val current: SituationalContext,
    val type: SituationChangeType,
    val significance: Float,
    val reason: String
)

data class SituationFocusCandidate(
    val title: String,
    val reason: String,
    val score: Float,
    val sources: Set<SituationSource>
)
