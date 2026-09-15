package com.example.action

import com.example.ai.ActionStatus

enum class ScreenNavCommand {
    HOME,
    BACK,
    RECENTS,
    NOTIFICATIONS
}

enum class VolumeDirection {
    INCREASE,
    DECREASE,
    SET_PERCENT,
    MUTE,
    UNMUTE
}

enum class MediaCommand {
    PLAY,
    PAUSE,
    NEXT,
    PREVIOUS,
    TOGGLE
}

enum class MemoryOpType {
    REMEMBER,
    RECALL,
    FORGET,
    CLEAR_ALL
}

sealed class NexusAction(
    val actionId: String = "ACT_" + System.currentTimeMillis() + "_" + (1000..9999).random(),
    val title: String,
    val requiresConfirmation: Boolean = false
) {
    data class LaunchApp(val appName: String) : NexusAction(
        title = "Open $appName"
    )

    data class ScreenNav(val navCommand: ScreenNavCommand) : NexusAction(
        title = "Navigation: $navCommand"
    )

    data class Volume(val direction: VolumeDirection, val percent: Int? = null) : NexusAction(
        title = "Volume: $direction ${percent?.let { "$it%" } ?: ""}".trim()
    )

    data class Media(val mediaCommand: MediaCommand) : NexusAction(
        title = "Media: $mediaCommand"
    )

    data class Memory(
        val opType: MemoryOpType,
        val key: String,
        val value: String? = null
    ) : NexusAction(
        title = "Memory: $opType $key",
        requiresConfirmation = opType == MemoryOpType.CLEAR_ALL
    )

    data class WhatsApp(val recipient: String, val message: String) : NexusAction(
        title = "WhatsApp to $recipient"
    )

    data class Sms(val phoneNumber: String, val message: String) : NexusAction(
        title = "Send SMS to $phoneNumber",
        requiresConfirmation = true
    )

    data class PhoneCall(val contactOrNumber: String) : NexusAction(
        title = "Call $contactOrNumber"
    )

    class OpenCalendar : NexusAction(
        title = "Open Calendar"
    )

    class ReadNotifications : NexusAction(
        title = "Read Notifications"
    )

    data class Routine(
        val routineName: String,
        val steps: List<NexusAction>
    ) : NexusAction(
        title = "Routine: $routineName"
    )
}

data class NexusActionResult(
    val actionId: String,
    val success: Boolean,
    val message: String,
    val status: ActionStatus,
    val speechFeedback: String? = null
)
