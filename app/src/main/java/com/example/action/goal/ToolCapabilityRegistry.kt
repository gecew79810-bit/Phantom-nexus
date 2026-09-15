package com.example.action.goal

import android.content.Context
import com.example.ai.RiskLevel
import com.example.permission.CentralPermissionsManager
import com.example.service.MaxAccessibilityService
import com.example.service.MaxNotificationBridge

data class RuntimeToolDescriptor(
    val toolId: String,
    val name: String,
    val enabled: Boolean,
    val permissionGranted: Boolean,
    val health: String, // HEALTHY, DEGRADED, PERMISSION_REQUIRED, OFFLINE, UNAVAILABLE, ERROR
    val riskLevel: RiskLevel,
    val estimatedLatencyMs: Long,
    val description: String
)

class ToolCapabilityRegistry private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: ToolCapabilityRegistry? = null

        fun getInstance(context: Context): ToolCapabilityRegistry {
            return instance ?: synchronized(this) {
                instance ?: ToolCapabilityRegistry(context.applicationContext).also { instance = it }
            }
        }
    }

    fun getAllRuntimeTools(): List<RuntimeToolDescriptor> {
        val hasCalendar = CentralPermissionsManager.hasCalendarPermission(context)
        val hasPhone = CentralPermissionsManager.hasPhonePermission(context)
        val hasSms = CentralPermissionsManager.hasSmsPermission(context)
        val hasAccessibility = MaxAccessibilityService.isConnected.value
        val hasNotification = MaxNotificationBridge.isServiceConnected.value

        return listOf(
            RuntimeToolDescriptor(
                toolId = "TOOL_CALENDAR",
                name = "Calendar & Schedules",
                enabled = true,
                permissionGranted = hasCalendar,
                health = if (hasCalendar) "HEALTHY" else "PERMISSION_REQUIRED",
                riskLevel = RiskLevel.LOW,
                estimatedLatencyMs = 80,
                description = "Read calendar schedules, find upcoming meetings, and check for clashes."
            ),
            RuntimeToolDescriptor(
                toolId = "TOOL_WEATHER",
                name = "Meteorological Intelligence",
                enabled = true,
                permissionGranted = true,
                health = "HEALTHY",
                riskLevel = RiskLevel.LOW,
                estimatedLatencyMs = 120,
                description = "Live meteorological data, precipitation forecasts, and weather alerts."
            ),
            RuntimeToolDescriptor(
                toolId = "TOOL_PRESENTATION_GEN",
                name = "Presentation & Slide Workflow",
                enabled = true,
                permissionGranted = true,
                health = "HEALTHY",
                riskLevel = RiskLevel.LOW,
                estimatedLatencyMs = 450,
                description = "Generate and structure presentation decks and meeting notes."
            ),
            RuntimeToolDescriptor(
                toolId = "TOOL_PDF_GEN",
                name = "PDF Document Generation",
                enabled = true,
                permissionGranted = true,
                health = "HEALTHY",
                riskLevel = RiskLevel.LOW,
                estimatedLatencyMs = 350,
                description = "Compile research summaries and comparisons into standalone PDF files."
            ),
            RuntimeToolDescriptor(
                toolId = "TOOL_TELECOM_CALL",
                name = "Direct Telephony Calling",
                enabled = true,
                permissionGranted = hasPhone,
                health = if (hasPhone) "HEALTHY" else "PERMISSION_REQUIRED",
                riskLevel = RiskLevel.HIGH,
                estimatedLatencyMs = 200,
                description = "Place outgoing phone calls to resolved contacts."
            ),
            RuntimeToolDescriptor(
                toolId = "TOOL_TELECOM_SMS",
                name = "Direct SMS Dispatch",
                enabled = true,
                permissionGranted = hasSms,
                health = if (hasSms) "HEALTHY" else "PERMISSION_REQUIRED",
                riskLevel = RiskLevel.HIGH,
                estimatedLatencyMs = 250,
                description = "Send SMS text messages to phone numbers."
            ),
            RuntimeToolDescriptor(
                toolId = "TOOL_ACCESSIBILITY_NAV",
                name = "Screen & Accessibility Navigation",
                enabled = hasAccessibility,
                permissionGranted = hasAccessibility,
                health = if (hasAccessibility) "HEALTHY" else "UNAVAILABLE",
                riskLevel = RiskLevel.LOW,
                estimatedLatencyMs = 100,
                description = "Global gestures, screen text inspection, tap and scroll."
            ),
            RuntimeToolDescriptor(
                toolId = "TOOL_NOTIFICATION_LISTENER",
                name = "Notification Bridge",
                enabled = hasNotification,
                permissionGranted = hasNotification,
                health = if (hasNotification) "HEALTHY" else "PERMISSION_REQUIRED",
                riskLevel = RiskLevel.LOW,
                estimatedLatencyMs = 60,
                description = "Triage unread alerts and incoming communications."
            ),
            RuntimeToolDescriptor(
                toolId = "TOOL_MEMORY_WALLET",
                name = "Encrypted Memory Wallet",
                enabled = true,
                permissionGranted = true,
                health = "HEALTHY",
                riskLevel = RiskLevel.LOW,
                estimatedLatencyMs = 40,
                description = "SQLCipher local storage for user preferences and facts."
            )
        )
    }

    private val toolOverrides = mutableMapOf<String, Pair<Boolean, String>>()

    fun setToolOverride(toolId: String, enabled: Boolean, health: String) {
        toolOverrides[toolId] = Pair(enabled, health)
    }

    fun clearOverrides() {
        toolOverrides.clear()
    }

    fun isToolAvailable(toolId: String): Boolean {
        toolOverrides[toolId]?.let { (enabled, health) ->
            return enabled && (health == "HEALTHY" || health == "DEGRADED")
        }
        val tool = getAllRuntimeTools().find { it.toolId == toolId } ?: return false
        return tool.enabled && (tool.health == "HEALTHY" || tool.health == "DEGRADED")
    }

    fun getToolDescriptor(toolId: String): RuntimeToolDescriptor? {
        val base = getAllRuntimeTools().find { it.toolId == toolId } ?: return null
        val override = toolOverrides[toolId]
        return if (override != null) {
            base.copy(enabled = override.first, health = override.second)
        } else {
            base
        }
    }
}
