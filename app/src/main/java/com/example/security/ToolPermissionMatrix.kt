package com.example.security

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import com.example.action.NexusAction
import com.example.ai.RiskLevel

enum class ConfirmationRequirement {
    NONE,
    IF_SENSITIVE,
    ALWAYS
}

enum class ActionReversibility {
    REVERSIBLE,
    IRREVERSIBLE
}

data class ToolSecurityProfile(
    val toolName: String,
    val requiredPermissions: List<String>,
    val confirmationRequirement: ConfirmationRequirement,
    val defaultRiskLevel: RiskLevel,
    val reversibility: ActionReversibility,
    val description: String
)

class ToolPermissionMatrix private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("trusted_automations_prefs", Context.MODE_PRIVATE)

    // Evaluates whether an action requires explicit user confirmation
    fun requiresConfirmation(action: NexusAction, isAutomation: Boolean = false, automationName: String? = null): Boolean {
        // Check trusted automation status
        if (isAutomation && automationName != null && isAutomationTrusted(automationName)) {
            // Highly trusted routine, only critical irreversible actions require confirmation
            val profile = getProfileForAction(action)
            return profile.confirmationRequirement == ConfirmationRequirement.ALWAYS && profile.reversibility == ActionReversibility.IRREVERSIBLE
        }

        val profile = getProfileForAction(action)
        return when (profile.confirmationRequirement) {
            ConfirmationRequirement.ALWAYS -> true
            ConfirmationRequirement.IF_SENSITIVE -> profile.defaultRiskLevel == RiskLevel.HIGH || profile.defaultRiskLevel == RiskLevel.CRITICAL
            ConfirmationRequirement.NONE -> false
        }
    }

    fun isAutomationTrusted(routineName: String): Boolean {
        // Default Morning Routine to trusted if user opted in
        val key = "trusted_routine_${routineName.lowercase().replace(" ", "_")}"
        return prefs.getBoolean(key, routineName.equals("Morning Routine", ignoreCase = true))
    }

    fun setAutomationTrusted(routineName: String, trusted: Boolean) {
        val key = "trusted_routine_${routineName.lowercase().replace(" ", "_")}"
        prefs.edit().putBoolean(key, trusted).apply()
    }

    fun getProfileForAction(action: NexusAction): ToolSecurityProfile {
        return when (action) {
            is NexusAction.LaunchApp -> ToolSecurityProfile(
                toolName = "LAUNCH_APP",
                requiredPermissions = emptyList(),
                confirmationRequirement = ConfirmationRequirement.NONE,
                defaultRiskLevel = RiskLevel.LOW,
                reversibility = ActionReversibility.REVERSIBLE,
                description = "Open requested installed application"
            )
            is NexusAction.ScreenNav -> ToolSecurityProfile(
                toolName = "SCREEN_NAVIGATION",
                requiredPermissions = listOf(Manifest.permission.BIND_ACCESSIBILITY_SERVICE),
                confirmationRequirement = ConfirmationRequirement.NONE,
                defaultRiskLevel = RiskLevel.LOW,
                reversibility = ActionReversibility.REVERSIBLE,
                description = "Navigate system home, back, or recents"
            )
            is NexusAction.Volume -> ToolSecurityProfile(
                toolName = "VOLUME_CONTROL",
                requiredPermissions = emptyList(),
                confirmationRequirement = ConfirmationRequirement.NONE,
                defaultRiskLevel = RiskLevel.LOW,
                reversibility = ActionReversibility.REVERSIBLE,
                description = "Adjust device volume or mute state"
            )
            is NexusAction.Media -> ToolSecurityProfile(
                toolName = "MEDIA_PLAYBACK",
                requiredPermissions = emptyList(),
                confirmationRequirement = ConfirmationRequirement.NONE,
                defaultRiskLevel = RiskLevel.LOW,
                reversibility = ActionReversibility.REVERSIBLE,
                description = "Control media playback stream"
            )
            is NexusAction.Sms -> ToolSecurityProfile(
                toolName = "SEND_SMS",
                requiredPermissions = listOf(Manifest.permission.SEND_SMS),
                confirmationRequirement = ConfirmationRequirement.ALWAYS,
                defaultRiskLevel = RiskLevel.HIGH,
                reversibility = ActionReversibility.IRREVERSIBLE,
                description = "Dispatch direct SMS to external contact"
            )
            is NexusAction.PhoneCall -> ToolSecurityProfile(
                toolName = "PHONE_CALL",
                requiredPermissions = listOf(Manifest.permission.CALL_PHONE),
                confirmationRequirement = ConfirmationRequirement.ALWAYS,
                defaultRiskLevel = RiskLevel.HIGH,
                reversibility = ActionReversibility.IRREVERSIBLE,
                description = "Initiate telephone voice call"
            )
            is NexusAction.OpenCalendar -> ToolSecurityProfile(
                toolName = "OPEN_CALENDAR",
                requiredPermissions = emptyList(),
                confirmationRequirement = ConfirmationRequirement.NONE,
                defaultRiskLevel = RiskLevel.LOW,
                reversibility = ActionReversibility.REVERSIBLE,
                description = "Open device calendar agenda"
            )
            is NexusAction.ReadNotifications -> ToolSecurityProfile(
                toolName = "READ_NOTIFICATIONS",
                requiredPermissions = listOf(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE),
                confirmationRequirement = ConfirmationRequirement.NONE,
                defaultRiskLevel = RiskLevel.LOW,
                reversibility = ActionReversibility.REVERSIBLE,
                description = "Read unread priority notifications"
            )
            is NexusAction.Memory -> ToolSecurityProfile(
                toolName = "MEMORY_OP",
                requiredPermissions = emptyList(),
                confirmationRequirement = if (action.requiresConfirmation) ConfirmationRequirement.ALWAYS else ConfirmationRequirement.NONE,
                defaultRiskLevel = if (action.requiresConfirmation) RiskLevel.HIGH else RiskLevel.LOW,
                reversibility = ActionReversibility.REVERSIBLE,
                description = "Read or write information in encrypted memory wallet"
            )
            is NexusAction.Routine -> ToolSecurityProfile(
                toolName = "MACRO_ROUTINE",
                requiredPermissions = emptyList(),
                confirmationRequirement = ConfirmationRequirement.IF_SENSITIVE,
                defaultRiskLevel = RiskLevel.MEDIUM,
                reversibility = ActionReversibility.REVERSIBLE,
                description = "Execute multi-step macro routine"
            )
            is NexusAction.WhatsApp -> ToolSecurityProfile(
                toolName = "WHATSAPP_SEND",
                requiredPermissions = emptyList(),
                confirmationRequirement = ConfirmationRequirement.IF_SENSITIVE,
                defaultRiskLevel = RiskLevel.MEDIUM,
                reversibility = ActionReversibility.REVERSIBLE,
                description = "Open WhatsApp conversation with prefilled text"
            )
        }
    }

    companion object {
        @Volatile
        private var instance: ToolPermissionMatrix? = null

        fun getInstance(context: Context): ToolPermissionMatrix {
            return instance ?: synchronized(this) {
                instance ?: ToolPermissionMatrix(context.applicationContext).also { instance = it }
            }
        }
    }
}
