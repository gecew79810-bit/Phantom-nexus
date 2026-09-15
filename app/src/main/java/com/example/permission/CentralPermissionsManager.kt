package com.example.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.service.MaxAccessibilityService

/**
 * Data model for system & hardware permissions managed by CentralPermissionsManager.
 */
enum class HardwarePermissionType {
    MICROPHONE,
    ACCESSIBILITY,
    OVERLAY,
    NOTIFICATIONS,
    PHONE_TELECOM,
    CALENDAR,
    CAMERA,
    CONTACTS,
    SMS,
    LOCATION
}

data class PermissionStatus(
    val type: HardwarePermissionType,
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val isCritical: Boolean = false
)

/**
 * CentralPermissionsManager:
 * Central engine to inspect hardware & system permissions (Microphone, Accessibility, Overlay, etc.)
 * and deep-link directly into specific system settings pages.
 */
object CentralPermissionsManager {

    private const val TAG = "PermissionsManager"

    /**
     * Checks if the microphone (RECORD_AUDIO) permission is granted.
     */
    fun hasMicrophonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if the MAX Accessibility Service is actively connected and running.
     */
    fun hasAccessibilityPermission(context: Context): Boolean {
        if (MaxAccessibilityService.isConnected.value) {
            return true
        }
        // Also check system enabled accessibility services string as fallback
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val expectedServiceName = "${context.packageName}/${MaxAccessibilityService::class.java.canonicalName}"
            val simpleServiceName = "${context.packageName}/${MaxAccessibilityService::class.java.name}"
            enabledServices.contains(expectedServiceName) || enabledServices.contains(simpleServiceName)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility permission: ${e.message}")
            false
        }
    }

    /**
     * Checks if the SYSTEM_ALERT_WINDOW (Display over other apps / Overlay) permission is granted.
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Checks if notification permission / listener is granted.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks phone call permission.
     */
    fun hasPhonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks calendar read/write permission.
     */
    fun hasCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks camera permission.
     */
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks contacts read permission.
     */
    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks SMS send permission.
     */
    fun hasSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks fine location permission.
     */
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Computes the complete snapshot of hardware permissions.
     */
    fun getPermissionStatuses(context: Context): List<PermissionStatus> {
        return listOf(
            PermissionStatus(
                type = HardwarePermissionType.MICROPHONE,
                title = "Microphone Access",
                description = "Required for low-latency neural voice commands and ambient listening.",
                isGranted = hasMicrophonePermission(context),
                isCritical = true
            ),
            PermissionStatus(
                type = HardwarePermissionType.ACCESSIBILITY,
                title = "Accessibility Service",
                description = "Required for screen text inspection, autonomous gestures, and UI automation.",
                isGranted = hasAccessibilityPermission(context),
                isCritical = true
            ),
            PermissionStatus(
                type = HardwarePermissionType.OVERLAY,
                title = "Display Over Other Apps (Overlay)",
                description = "Required for floating heads-up display (HUD), floating mic, and quick actions.",
                isGranted = hasOverlayPermission(context),
                isCritical = true
            ),
            PermissionStatus(
                type = HardwarePermissionType.NOTIFICATIONS,
                title = "Notifications & Bridge",
                description = "Allows incoming alerts and background notification processing.",
                isGranted = hasNotificationPermission(context),
                isCritical = false
            ),
            PermissionStatus(
                type = HardwarePermissionType.PHONE_TELECOM,
                title = "Phone & Telecom",
                description = "Enables caller screening, auto-answer, and voice dialer integration.",
                isGranted = hasPhonePermission(context),
                isCritical = false
            ),
            PermissionStatus(
                type = HardwarePermissionType.CALENDAR,
                title = "Calendar Access",
                description = "Allows reading schedules, planning routines, and setting agenda reminders.",
                isGranted = hasCalendarPermission(context),
                isCritical = false
            ),
            PermissionStatus(
                type = HardwarePermissionType.CAMERA,
                title = "Camera Vision",
                description = "Required for OCR screen scanning and multimodal visual understanding.",
                isGranted = hasCameraPermission(context),
                isCritical = false
            ),
            PermissionStatus(
                type = HardwarePermissionType.CONTACTS,
                title = "Contacts Directory",
                description = "Allows looking up contacts for hands-free voice calls and messaging.",
                isGranted = hasContactsPermission(context),
                isCritical = false
            ),
            PermissionStatus(
                type = HardwarePermissionType.SMS,
                title = "SMS Messaging",
                description = "Enables sending emergency SMS and dictated text messages.",
                isGranted = hasSmsPermission(context),
                isCritical = false
            ),
            PermissionStatus(
                type = HardwarePermissionType.LOCATION,
                title = "Device Location",
                description = "Enables weather, navigation, and location-aware routine triggers.",
                isGranted = hasLocationPermission(context),
                isCritical = false
            )
        )
    }

    /**
     * Returns true if any of the core hardware permissions (Microphone, Accessibility, Overlay) are missing.
     */
    fun hasMissingCorePermissions(context: Context): Boolean {
        return !hasMicrophonePermission(context) ||
                !hasAccessibilityPermission(context) ||
                !hasOverlayPermission(context)
    }

    /**
     * Returns the list of missing critical permissions.
     */
    fun getMissingCorePermissions(context: Context): List<HardwarePermissionType> {
        val missing = mutableListOf<HardwarePermissionType>()
        if (!hasMicrophonePermission(context)) missing.add(HardwarePermissionType.MICROPHONE)
        if (!hasAccessibilityPermission(context)) missing.add(HardwarePermissionType.ACCESSIBILITY)
        if (!hasOverlayPermission(context)) missing.add(HardwarePermissionType.OVERLAY)
        return missing
    }

    // =========================================================================
    // SYSTEM DEEP-LINKS
    // =========================================================================

    /**
     * Deep-links the user directly to the app details settings page for Microphone / Runtime permissions.
     */
    fun openMicrophoneSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app details settings: ${e.message}")
            openGeneralSettings(context)
        }
    }

    /**
     * Deep-links the user directly to the system Accessibility settings page.
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open accessibility settings: ${e.message}")
            openGeneralSettings(context)
        }
    }

    /**
     * Deep-links the user directly to the system Display Over Other Apps (Overlay) permission settings.
     */
    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open overlay permission settings: ${e.message}")
                // Fallback to generic overlay list
                try {
                    val genericIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(genericIntent)
                } catch (e2: Exception) {
                    openAppDetailsSettings(context)
                }
            }
        } else {
            openAppDetailsSettings(context)
        }
    }

    /**
     * Deep-links to system Notification Listener settings.
     */
    fun openNotificationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppDetailsSettings(context)
        }
    }

    /**
     * Opens application details settings.
     */
    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openGeneralSettings(context)
        }
    }

    /**
     * Fallback to general settings.
     */
    fun openGeneralSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot launch settings intent: ${e.message}")
        }
    }

    /**
     * Dispatches deep-link directly for any given permission type.
     */
    fun openSettingsForPermission(context: Context, type: HardwarePermissionType) {
        when (type) {
            HardwarePermissionType.MICROPHONE -> openMicrophoneSettings(context)
            HardwarePermissionType.ACCESSIBILITY -> openAccessibilitySettings(context)
            HardwarePermissionType.OVERLAY -> openOverlaySettings(context)
            HardwarePermissionType.NOTIFICATIONS -> openNotificationSettings(context)
            HardwarePermissionType.PHONE_TELECOM,
            HardwarePermissionType.CALENDAR,
            HardwarePermissionType.CAMERA,
            HardwarePermissionType.CONTACTS,
            HardwarePermissionType.SMS,
            HardwarePermissionType.LOCATION -> openAppDetailsSettings(context)
        }
    }
}
