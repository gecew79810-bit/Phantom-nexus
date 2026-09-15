package com.pantham.nexus.situational.adapters

import com.pantham.nexus.intelligence.context.NexusRuntimeContextProvider
import com.pantham.nexus.situational.model.*
import com.pantham.nexus.situational.signal.*

class ContextDrivenDeviceSituationProvider(
    private val runtimeProvider: NexusRuntimeContextProvider
) : DeviceSituationProvider {
    override suspend fun getDeviceSituation(): DeviceSituation {
        val battery = runtimeProvider.batteryPercent() ?: -1
        val network = runtimeProvider.networkAvailable()
        return DeviceSituation(
            screenOn = true,
            batteryPercent = battery,
            batteryLow = battery in 0..15,
            wifiConnected = network
        )
    }
}

class ContextDrivenAppSituationProvider(
    private val runtimeProvider: NexusRuntimeContextProvider,
    private val appNameResolver: ((String) -> String?)? = null
) : AppSituationProvider {
    override suspend fun getAppSituation(): AppSituation {
        val pkg = runtimeProvider.currentPackage()
        val category = pkg?.let { inferAppCategory(it) }
        val appName = pkg?.let { appNameResolver?.invoke(it) ?: deriveAppName(it) }

        return AppSituation(
            packageName = pkg,
            appName = appName,
            category = category,
            foreground = !pkg.isNullOrBlank()
        )
    }

    private fun inferAppCategory(pkg: String): String {
        val lower = pkg.lowercase()
        return when {
            lower.contains("map") || lower.contains("navigation") || lower.contains("waze") -> "navigation"
            lower.contains("youtube") || lower.contains("netflix") || lower.contains("video") || lower.contains("player") -> "video"
            lower.contains("whatsapp") || lower.contains("telegram") || lower.contains("message") || lower.contains("dialer") -> "communication"
            lower.contains("amazon") || lower.contains("flipkart") || lower.contains("shop") -> "shopping"
            else -> "application"
        }
    }

    private fun deriveAppName(pkg: String): String {
        return pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}

class ContextDrivenNetworkSituationProvider(
    private val runtimeProvider: NexusRuntimeContextProvider
) : NetworkSituationProvider {
    override suspend fun getNetworkSituation(): NetworkSituation {
        val connected = runtimeProvider.networkAvailable()
        return NetworkSituation(
            connected = connected,
            transport = if (connected) "wifi_or_cellular" else null,
            validated = connected
        )
    }
}

class StateDrivenSessionSituationProvider(
    private val isListeningProvider: () -> Boolean = { false },
    private val isSpeakingProvider: () -> Boolean = { false },
    private val activeSessionIdProvider: () -> String? = { null },
    private val interruptionAllowedProvider: () -> Boolean = { true }
) : SessionSituationProvider {
    override suspend fun getSessionSituation(): SessionSituation {
        val speaking = isSpeakingProvider()
        val listening = isListeningProvider()
        val sessionId = activeSessionIdProvider()
        val active = speaking || listening || (sessionId != null)

        return SessionSituation(
            active = active,
            listening = listening,
            speaking = speaking,
            voiceMode = speaking || listening,
            sessionId = sessionId,
            interruptionAllowed = interruptionAllowedProvider()
        )
    }
}

class StateDrivenNotificationSituationProvider(
    private val unreadCountProvider: () -> Int = { 0 },
    private val urgentCountProvider: () -> Int = { 0 },
    private val latestPackageProvider: () -> String? = { null },
    private val hasActionableProvider: () -> Boolean = { false }
) : NotificationSituationProvider {
    override suspend fun getNotificationSituation(): NotificationSituation {
        return NotificationSituation(
            unreadCount = unreadCountProvider(),
            urgentCount = urgentCountProvider(),
            latestPackage = latestPackageProvider(),
            hasActionableNotification = hasActionableProvider()
        )
    }
}

class StateDrivenGoalSituationProvider(
    private val goalProvider: () -> GoalSituation? = { null }
) : GoalSituationProvider {
    override suspend fun getGoalSituation(): GoalSituation {
        return goalProvider() ?: GoalSituation()
    }
}

class StateDrivenActionSituationProvider(
    private val actionProvider: () -> ActionSituation? = { null }
) : ActionSituationProvider {
    override suspend fun getActionSituation(): ActionSituation {
        return actionProvider() ?: ActionSituation()
    }
}

class ContextDrivenPredictionSituationProvider(
    private val predictiveContextProvider: () -> com.pantham.nexus.prediction.PredictiveContext? = { null }
) : PredictionSituationProvider {
    override suspend fun getPredictionSituation(): PredictionSituation {
        val pred = predictiveContextProvider() ?: return PredictionSituation()
        val top = pred.predictions.maxByOrNull { it.confidenceScore }
        return PredictionSituation(
            strongestPrediction = top?.title ?: top?.predictedAction,
            confidence = top?.confidenceScore ?: 0f,
            predictionCount = pred.predictions.size
        )
    }
}

class ContextDrivenVisionSituationProvider(
    private val visualContextProvider: () -> com.pantham.nexus.vision.model.VisualContext? = { null }
) : VisionSituationProvider {
    override suspend fun getVisionSituation(): VisionSituation {
        val vision = visualContextProvider() ?: return VisionSituation()
        val avgConfidence = if (vision.detectedObjects.isNotEmpty()) {
            vision.detectedObjects.map { it.confidence }.average().toFloat()
        } else {
            0.85f
        }
        return VisionSituation(
            available = true,
            summary = vision.description,
            confidence = avgConfidence,
            objectCount = vision.detectedObjects.size,
            textDetected = vision.visibleText.isNotBlank()
        )
    }
}

class ContextDrivenTemporalSituationProvider(
    private val temporalProvider: () -> TemporalSituation? = { null }
) : TemporalSituationProvider {
    override suspend fun getTemporalSituation(): TemporalSituation {
        return temporalProvider() ?: TemporalSituation()
    }
}
