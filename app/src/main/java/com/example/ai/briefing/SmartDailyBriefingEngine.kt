package com.example.ai.briefing

import android.content.Context
import com.example.bridge.AndroidSystemBridge
import com.example.media.MaxMediaManager
import com.example.permission.CentralPermissionsManager
import com.example.security.NexusFeatureFlags
import com.example.service.MaxNotificationBridge
import com.example.voice.AssistantLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmartDailyBriefingEngine(
    private val context: Context,
    private val systemBridge: AndroidSystemBridge,
    private val mediaManager: MaxMediaManager?,
    private val featureFlags: NexusFeatureFlags
) {
    suspend fun generateBriefing(language: AssistantLanguage): String = coroutineScope {
        val weatherDeferred = async(Dispatchers.IO) {
            "28°C, Clear and pleasant"
        }

        val calendarDeferred = async(Dispatchers.IO) {
            if (featureFlags.calendarEnabled.value && CentralPermissionsManager.hasCalendarPermission(context)) {
                "2 scheduled meetings today"
            } else {
                null // Not authorized or disabled
            }
        }

        val notificationsDeferred = async(Dispatchers.IO) {
            if (featureFlags.notificationsEnabled.value) {
                val summary = MaxNotificationBridge.getPriorityNotificationsSummary()
                if (summary != "No critical or priority alerts.") summary else null
            } else {
                null
            }
        }

        val weather = weatherDeferred.await()
        val calendar = calendarDeferred.await()
        val notifs = notificationsDeferred.await()

        val timeHour = SimpleDateFormat("HH", Locale.getDefault()).format(Date()).toIntOrNull() ?: 10
        val isMorning = timeHour in 5..11
        val isEvening = timeHour in 17..22

        if (language == AssistantLanguage.HINDI) {
            val greeting = when {
                isMorning -> "सुप्रभात बॉस!"
                isEvening -> "शुभ संध्या बॉस!"
                else -> "नमस्ते बॉस!"
            }
            val builder = StringBuilder(greeting)
            builder.append(" आज मौसम $weather है।")
            if (calendar != null) {
                builder.append(" आपके आज $calendar हैं।")
            }
            if (notifs != null) {
                builder.append(" $notifs")
            }
            builder.append(" सब कुछ नियंत्रण में है।")
            builder.toString()
        } else {
            val greeting = when {
                isMorning -> "Good morning."
                isEvening -> "Good evening."
                else -> "Hello."
            }
            val builder = StringBuilder(greeting)
            builder.append(" The weather is $weather.")
            if (calendar != null) {
                builder.append(" You have $calendar.")
            }
            if (notifs != null) {
                builder.append(" Notifications: $notifs.")
            }
            builder.append(" All systems are nominal.")
            builder.toString()
        }
    }
}
