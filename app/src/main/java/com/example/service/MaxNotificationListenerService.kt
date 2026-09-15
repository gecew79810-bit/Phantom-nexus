package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CapturedNotification(
    val id: String,
    val packageName: String,
    val appName: String,
    val sender: String,
    val text: String,
    val timestamp: String,
    val hasReplyAction: Boolean,
    val isAutoReplied: Boolean = false,
    val autoReplyContent: String? = null,
    val isPriority: Boolean = false,
    val requiresResponse: Boolean = false
)

data class NotificationGroup(
    val appName: String,
    val sender: String,
    val notifications: List<CapturedNotification>,
    val requiresResponse: Boolean
)

object MaxNotificationBridge {
    private val _notifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val notifications: StateFlow<List<CapturedNotification>> = _notifications.asStateFlow()

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    private val _isAutoReplyEnabled = MutableStateFlow(false)
    val isAutoReplyEnabled: StateFlow<Boolean> = _isAutoReplyEnabled.asStateFlow()

    // Map of notification id to pending reply action & remote input
    val replyActionsMap = mutableMapOf<String, Pair<Notification.Action, RemoteInput>>()

    fun setConnected(connected: Boolean) {
        _isServiceConnected.value = connected
    }

    fun toggleAutoReply() {
        _isAutoReplyEnabled.value = !_isAutoReplyEnabled.value
    }

    fun addNotification(notification: CapturedNotification) {
        val current = _notifications.value.toMutableList()
        // Deduplicate or append
        current.removeAll { it.id == notification.id }
        current.add(0, notification)
        if (current.size > 50) {
            current.removeAt(current.size - 1)
        }
        _notifications.value = current
    }

    fun markAutoReplied(id: String, reply: String) {
        val current = _notifications.value.map {
            if (it.id == id) it.copy(isAutoReplied = true, autoReplyContent = reply) else it
        }
        _notifications.value = current
    }

    fun getGroupedNotifications(): List<NotificationGroup> {
        return _notifications.value.groupBy { "${it.appName}_${it.sender}" }.map { entry ->
            val notifs = entry.value
            NotificationGroup(
                appName = notifs.first().appName,
                sender = notifs.first().sender,
                notifications = notifs,
                requiresResponse = notifs.any { it.requiresResponse }
            )
        }
    }

    fun getPriorityNotificationsSummary(): String {
        val urgent = _notifications.value.filter { it.isPriority || it.requiresResponse }
        if (urgent.isEmpty()) return "No critical or priority alerts."
        return urgent.take(3).joinToString(". ") {
            "${it.appName} alert from ${it.sender}: ${it.text.take(60)}"
        }
    }

    /**
     * Dispatches autonomous remote input inline reply directly in background
     */
    fun sendInlineReply(context: Context, notificationId: String, replyText: String): Boolean {
        val pair = replyActionsMap[notificationId] ?: return false
        val action = pair.first
        val remoteInput = pair.second

        return try {
            val intent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(remoteInput.resultKey, replyText)
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
            action.actionIntent.send(context, 0, intent)
            markAutoReplied(notificationId, replyText)
            Log.i("MaxNotificationBridge", "Inline reply dispatched successfully to $notificationId")
            true
        } catch (e: Exception) {
            Log.e("MaxNotificationBridge", "Error sending inline reply", e)
            false
        }
    }
}

class MaxNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        MaxNotificationBridge.setConnected(true)
        Log.i("MaxNotificationService", "Notification listener connected.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        MaxNotificationBridge.setConnected(false)
        Log.i("MaxNotificationService", "Notification listener disconnected.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        val extras = sbn.notification?.extras ?: return

        // Support WhatsApp, Telegram, Instagram, SMS, Messages
        val supportedPackages = setOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.instagram.android",
            "com.google.android.apps.messaging",
            "com.android.mms"
        )

        val isTargetApp = supportedPackages.contains(pkg) || pkg.contains("messaging") || pkg.contains("sms")
        if (!isTargetApp) return

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (text.isBlank() && title.isBlank()) return

        // Extract RemoteInput Reply Action if present
        var replyPair: Pair<Notification.Action, RemoteInput>? = null
        val actions = sbn.notification?.actions
        if (actions != null) {
            for (action in actions) {
                val remoteInputs = action.remoteInputs
                if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                    replyPair = Pair(action, remoteInputs[0])
                    break
                }
            }
        }

        val appName = when (pkg) {
            "com.whatsapp" -> "WhatsApp"
            "org.telegram.messenger" -> "Telegram"
            "com.instagram.android" -> "Instagram"
            "com.google.android.apps.messaging", "com.android.mms" -> "SMS"
            else -> pkg.substringAfterLast('.')
        }

        val notifId = "${sbn.id}_${sbn.postTime}"
        if (replyPair != null) {
            MaxNotificationBridge.replyActionsMap[notifId] = replyPair
        }

        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(sbn.postTime))
        val lowerText = text.lowercase()
        val isPriority = lowerText.contains("urgent") || lowerText.contains("otp") || lowerText.contains("emergency") || lowerText.contains("important") || lowerText.contains("code") || lowerText.contains("alert")
        val requiresResponse = text.contains("?") || lowerText.contains("kahan") || lowerText.contains("where") || lowerText.contains("kab") || lowerText.contains("when") || lowerText.contains("reply") || lowerText.contains("call me")

        val captured = CapturedNotification(
            id = notifId,
            packageName = pkg,
            appName = appName,
            sender = title.ifBlank { "Direct Contact" },
            text = text,
            timestamp = timeStr,
            hasReplyAction = replyPair != null,
            isPriority = isPriority,
            requiresResponse = requiresResponse
        )

        MaxNotificationBridge.addNotification(captured)

        // Autonomous Auto-Reply execution if enabled and reply action exists
        if (MaxNotificationBridge.isAutoReplyEnabled.value && replyPair != null) {
            val autoReplyText = "⚡ MAX AI: Boss is currently unavailable. Your message has been logged securely."
            MaxNotificationBridge.sendInlineReply(applicationContext, notifId, autoReplyText)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.let {
            val notifId = "${it.id}_${it.postTime}"
            MaxNotificationBridge.replyActionsMap.remove(notifId)
        }
    }
}
