package com.pantham.nexus.task.observation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.example.hardware.HardwareController
import com.pantham.nexus.situational.model.SituationState
import com.pantham.nexus.situational.runtime.NexusSituationRuntime
import com.pantham.nexus.task.model.ContextChangeSeverity
import com.pantham.nexus.task.model.ContextEvaluation
import com.pantham.nexus.task.model.TaskObservationSnapshot

/**
 * Observes real-time situational, device, and network conditions during autonomous execution.
 */
class ExecutionEnvironmentObserver(
    private val context: Context? = null,
    private val hardwareController: HardwareController? = null
) {

    fun captureSnapshot(): TaskObservationSnapshot {
        var networkAvailable = true
        var isWifi = true
        var batteryPct = 100
        var isLowBattery = false
        var situationState = SituationState.UNKNOWN

        // 1. Check network
        context?.let { ctx ->
            try {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNetwork = cm?.activeNetwork
                val caps = cm?.getNetworkCapabilities(activeNetwork)
                networkAvailable = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } catch (_: Exception) {}

            try {
                val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
                isLowBattery = batteryPct <= 15
            } catch (_: Exception) {}
        }

        // 2. Check situational awareness runtime if available
        var isTraveling = false
        try {
            val engine = NexusSituationRuntime.engine
            val ctx = kotlinx.coroutines.runBlocking { engine.buildContext() }
            situationState = ctx.activeState
            isTraveling = ctx.activity.mode == com.pantham.nexus.situational.model.ActivityMode.TRAVELING
        } catch (_: Throwable) {}

        return TaskObservationSnapshot(
            timestamp = System.currentTimeMillis(),
            situationState = situationState,
            networkAvailable = networkAvailable,
            isWifi = isWifi,
            batteryPercent = batteryPct,
            isLowBattery = isLowBattery,
            isUserDriving = isTraveling,
            isAudioBusy = false
        )
    }

    /**
     * Compares an initial or previous snapshot against the current snapshot to determine
     * whether the execution plan needs to be re-evaluated, paused, or adapted.
     */
    fun evaluateContextChange(
        initialSnapshot: TaskObservationSnapshot,
        currentSnapshot: TaskObservationSnapshot,
        stepRequiresNetwork: Boolean = false,
        stepRequiresScreen: Boolean = false
    ): ContextEvaluation {
        // Critical: Network dropped when required
        if (stepRequiresNetwork && !currentSnapshot.networkAvailable && initialSnapshot.networkAvailable) {
            return ContextEvaluation(
                severity = ContextChangeSeverity.CRITICAL_REPLAN_NEEDED,
                reason = "Network connectivity lost during online task step. Re-planning or offline fallback required.",
                requiresPauseOrReplan = true
            )
        }

        // Critical: User started driving while task requires visual screen attention
        if (stepRequiresScreen && currentSnapshot.isUserDriving && !initialSnapshot.isUserDriving) {
            return ContextEvaluation(
                severity = ContextChangeSeverity.CRITICAL_REPLAN_NEEDED,
                reason = "User started driving. Visual interaction must be switched to audio-only or deferred.",
                requiresPauseOrReplan = true
            )
        }

        // Critical: Battery critically low
        if (currentSnapshot.batteryPercent <= 10 && !initialSnapshot.isLowBattery) {
            return ContextEvaluation(
                severity = ContextChangeSeverity.MEDIUM,
                reason = "Battery dropped to critical ${currentSnapshot.batteryPercent}%. Non-essential steps should be pruned.",
                requiresPauseOrReplan = false
            )
        }

        // Low / Medium: Switched from WiFi to mobile data
        if (initialSnapshot.isWifi && !currentSnapshot.isWifi && currentSnapshot.networkAvailable) {
            return ContextEvaluation(
                severity = ContextChangeSeverity.LOW,
                reason = "Switched from Wi-Fi to cellular data. High-bandwidth downloads should optimize data usage.",
                requiresPauseOrReplan = false
            )
        }

        return ContextEvaluation(
            severity = ContextChangeSeverity.NONE,
            reason = "Operating conditions remain nominal.",
            requiresPauseOrReplan = false
        )
    }
}
