package com.example.context

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.hardware.HardwareController
import com.example.media.MaxMediaManager
import com.example.service.MaxAccessibilityService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NexusSystemContext(
    val currentApp: String? = null,
    val currentScreenText: String? = null,
    val activeTask: String? = null,
    val recentCommands: List<String> = emptyList(),
    val activeAutomation: String? = null,
    val relevantMemory: List<String> = emptyList(),
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val currentTimeFormatted: String = "",
    val locationCity: String? = null,
    val calendarContext: String? = null,
    val currentMediaTrack: String? = null,
    val lastInteractedContact: String? = null,
    val lastPendingTopic: String? = null
)

class NexusContextEngine(
    private val context: Context,
    private val hardwareController: HardwareController,
    private val mediaManager: MaxMediaManager? = null
) {
    private val recentCommandsHistory = mutableListOf<String>()
    var lastInteractedContact: String? = null
        private set
    var lastPendingTopic: String? = null
        private set

    fun recordCommand(command: String) {
        synchronized(recentCommandsHistory) {
            recentCommandsHistory.add(command)
            if (recentCommandsHistory.size > 8) {
                recentCommandsHistory.removeAt(0)
            }
        }
    }

    fun setLastContact(contact: String) {
        lastInteractedContact = contact
    }

    fun setLastTopic(topic: String) {
        lastPendingTopic = topic
    }

    fun assembleCurrentContext(
        activeTask: String? = null,
        activeAutomation: String? = null,
        relevantMemory: List<String> = emptyList(),
        calendarSummary: String? = null
    ): NexusSystemContext {
        // Battery status
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent = context.registerReceiver(null, batteryFilter)
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100) / scale else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val screenText = MaxAccessibilityService.lastCapturedScreenText.value.takeIf { it.isNotBlank() }

        val timeFormatter = SimpleDateFormat("EEEE, dd MMMM yyyy, hh:mm a", Locale.getDefault())
        val timeString = timeFormatter.format(Date())

        val mediaTrack = mediaManager?.mediaState?.value?.let {
            if (it.title.isNotBlank()) "${it.title} by ${it.artist}" else null
        }

        return NexusSystemContext(
            currentApp = null,
            currentScreenText = screenText?.take(500),
            activeTask = activeTask,
            recentCommands = synchronized(recentCommandsHistory) { recentCommandsHistory.toList() },
            activeAutomation = activeAutomation,
            relevantMemory = relevantMemory,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            currentTimeFormatted = timeString,
            locationCity = "India",
            calendarContext = calendarSummary,
            currentMediaTrack = mediaTrack,
            lastInteractedContact = lastInteractedContact,
            lastPendingTopic = lastPendingTopic
        )
    }

    // Selectively injects only relevant context pieces into AI prompt
    fun pruneForQuery(query: String, fullContext: NexusSystemContext): String {
        val q = query.lowercase()
        val builder = StringBuilder()

        builder.append("System Time: ${fullContext.currentTimeFormatted}\n")

        // Screen context if user is asking about current screen or app
        if ((q.contains("screen") || q.contains("dekh") || q.contains("padho") || q.contains("read") || q.contains("page")) && !fullContext.currentScreenText.isNullOrBlank()) {
            builder.append("Current Screen Content: \"${fullContext.currentScreenText}\"\n")
        }

        // Battery context
        if (q.contains("battery") || q.contains("charge") || q.contains("power") || fullContext.batteryPercent <= 20) {
            builder.append("Device Battery: ${fullContext.batteryPercent}% (Charging: ${fullContext.isCharging})\n")
        }

        // Calendar context
        if ((q.contains("calendar") || q.contains("schedule") || q.contains("meeting") || q.contains("aaj") || q.contains("today")) && !fullContext.calendarContext.isNullOrBlank()) {
            builder.append("Calendar Schedule: ${fullContext.calendarContext}\n")
        }

        // Media context
        if ((q.contains("music") || q.contains("gana") || q.contains("play") || q.contains("song")) && !fullContext.currentMediaTrack.isNullOrBlank()) {
            builder.append("Now Playing: ${fullContext.currentMediaTrack}\n")
        }

        // Contextual pronoun resolution (e.g. "usko", "them", "him")
        if ((q.contains("usko") || q.contains("him") || q.contains("her") || q.contains("them") || q.contains("same person")) && !fullContext.lastInteractedContact.isNullOrBlank()) {
            builder.append("Last Referred Contact: \"${fullContext.lastInteractedContact}\"\n")
        }

        // Memory facts
        if (fullContext.relevantMemory.isNotEmpty()) {
            builder.append("Relevant User Memory:\n")
            fullContext.relevantMemory.take(3).forEach {
                builder.append("- $it\n")
            }
        }

        return builder.toString().trim()
    }
}
