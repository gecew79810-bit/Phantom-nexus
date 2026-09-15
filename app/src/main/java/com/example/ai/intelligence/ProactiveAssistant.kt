package com.example.ai.intelligence

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.security.NexusFeatureFlags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProactiveSuggestion(
    val id: String = System.currentTimeMillis().toString(),
    val title: String,
    val message: String,
    val actionType: String? = null,
    val iconType: String = "INFO"
)

class ProactiveAssistant(
    private val context: Context,
    private val featureFlags: NexusFeatureFlags
) {
    private val _activeSuggestion = MutableStateFlow<ProactiveSuggestion?>(null)
    val activeSuggestion: StateFlow<ProactiveSuggestion?> = _activeSuggestion.asStateFlow()

    private var lastLowBatteryAlertDismissed = false
    private val dismissedActionTypes = mutableSetOf<String>()
    private val lastPresentedTimestamps = mutableMapOf<String, Long>()

    fun checkProactiveConditions(
        upcomingMeetingNotice: String? = null,
        unreadUrgentNotifs: Int = 0,
        routineOpportunity: String? = null,
        unfinishedSafeTaskNotice: String? = null
    ) {
        // Master setting check
        if (!featureFlags.proactiveEnabled.value) {
            _activeSuggestion.value = null
            return
        }

        val now = System.currentTimeMillis()
        val cooldownMs = 5 * 60 * 1000L // 5-minute cooldown to prevent repetitive spam

        fun canShow(type: String): Boolean {
            if (dismissedActionTypes.contains(type)) return false
            val lastShown = lastPresentedTimestamps[type] ?: 0L
            return (now - lastShown) > cooldownMs
        }

        // 1. Battery check
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent = context.registerReceiver(null, batteryFilter)
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        if (batteryPct <= 15 && !isCharging) {
            if (!lastLowBatteryAlertDismissed && canShow("BATTERY_SAVER")) {
                lastPresentedTimestamps["BATTERY_SAVER"] = now
                _activeSuggestion.value = ProactiveSuggestion(
                    title = "Battery Low ($batteryPct%)",
                    message = "Would you like to activate Extreme Battery Saver mode?",
                    actionType = "BATTERY_SAVER",
                    iconType = "BATTERY"
                )
                return
            }
        } else {
            lastLowBatteryAlertDismissed = false
            dismissedActionTypes.remove("BATTERY_SAVER")
        }

        // 2. Calendar notice
        if (!upcomingMeetingNotice.isNullOrBlank() && canShow("CALENDAR")) {
            lastPresentedTimestamps["CALENDAR"] = now
            _activeSuggestion.value = ProactiveSuggestion(
                title = "Upcoming Meeting",
                message = upcomingMeetingNotice,
                actionType = "CALENDAR",
                iconType = "CALENDAR"
            )
            return
        }

        // 3. Urgent notifications
        if (unreadUrgentNotifs > 0 && canShow("NOTIFICATIONS")) {
            lastPresentedTimestamps["NOTIFICATIONS"] = now
            _activeSuggestion.value = ProactiveSuggestion(
                title = "Priority Alerts",
                message = "You have $unreadUrgentNotifs unread priority messages.",
                actionType = "NOTIFICATIONS",
                iconType = "NOTIF"
            )
            return
        }

        // 4. Active routine opportunity
        if (!routineOpportunity.isNullOrBlank() && canShow("ROUTINE")) {
            lastPresentedTimestamps["ROUTINE"] = now
            _activeSuggestion.value = ProactiveSuggestion(
                title = "Routine Opportunity",
                message = routineOpportunity,
                actionType = "ROUTINE",
                iconType = "ROUTINE"
            )
            return
        }

        // 5. Unfinished safe task
        if (!unfinishedSafeTaskNotice.isNullOrBlank() && canShow("UNFINISHED_TASK")) {
            lastPresentedTimestamps["UNFINISHED_TASK"] = now
            _activeSuggestion.value = ProactiveSuggestion(
                title = "Unfinished Task",
                message = unfinishedSafeTaskNotice,
                actionType = "UNFINISHED_TASK",
                iconType = "TASK"
            )
            return
        }

        _activeSuggestion.value = null
    }

    fun dismissSuggestion() {
        _activeSuggestion.value?.actionType?.let { type ->
            dismissedActionTypes.add(type)
            if (type == "BATTERY_SAVER") {
                lastLowBatteryAlertDismissed = true
            }
        }
        _activeSuggestion.value = null
    }

    fun resetDismissed() {
        dismissedActionTypes.clear()
        lastPresentedTimestamps.clear()
        lastLowBatteryAlertDismissed = false
    }
}
