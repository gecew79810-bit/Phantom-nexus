package com.example.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NexusFeatureFlags private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("nexus_feature_flags", Context.MODE_PRIVATE)

    private val _voiceEnabled = MutableStateFlow(prefs.getBoolean(KEY_VOICE, true))
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled.asStateFlow()

    private val _visionEnabled = MutableStateFlow(prefs.getBoolean(KEY_VISION, true))
    val visionEnabled: StateFlow<Boolean> = _visionEnabled.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATIONS, true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _automationEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTOMATION, true))
    val automationEnabled: StateFlow<Boolean> = _automationEnabled.asStateFlow()

    private val _memoryEnabled = MutableStateFlow(prefs.getBoolean(KEY_MEMORY, true))
    val memoryEnabled: StateFlow<Boolean> = _memoryEnabled.asStateFlow()

    private val _calendarEnabled = MutableStateFlow(prefs.getBoolean(KEY_CALENDAR, true))
    val calendarEnabled: StateFlow<Boolean> = _calendarEnabled.asStateFlow()

    private val _mediaEnabled = MutableStateFlow(prefs.getBoolean(KEY_MEDIA, true))
    val mediaEnabled: StateFlow<Boolean> = _mediaEnabled.asStateFlow()

    private val _researchEnabled = MutableStateFlow(prefs.getBoolean(KEY_RESEARCH, true))
    val researchEnabled: StateFlow<Boolean> = _researchEnabled.asStateFlow()

    private val _proactiveEnabled = MutableStateFlow(prefs.getBoolean(KEY_PROACTIVE, true))
    val proactiveEnabled: StateFlow<Boolean> = _proactiveEnabled.asStateFlow()

    fun setFeature(key: String, enabled: Boolean) {
        prefs.edit().putBoolean(key, enabled).apply()
        when (key) {
            KEY_VOICE -> _voiceEnabled.value = enabled
            KEY_VISION -> _visionEnabled.value = enabled
            KEY_NOTIFICATIONS -> _notificationsEnabled.value = enabled
            KEY_AUTOMATION -> _automationEnabled.value = enabled
            KEY_MEMORY -> _memoryEnabled.value = enabled
            KEY_CALENDAR -> _calendarEnabled.value = enabled
            KEY_MEDIA -> _mediaEnabled.value = enabled
            KEY_RESEARCH -> _researchEnabled.value = enabled
            KEY_PROACTIVE -> _proactiveEnabled.value = enabled
        }
    }

    companion object {
        const val KEY_VOICE = "voice.enabled"
        const val KEY_VISION = "vision.enabled"
        const val KEY_NOTIFICATIONS = "notifications.enabled"
        const val KEY_AUTOMATION = "automation.enabled"
        const val KEY_MEMORY = "memory.enabled"
        const val KEY_CALENDAR = "calendar.enabled"
        const val KEY_MEDIA = "media.enabled"
        const val KEY_RESEARCH = "research.enabled"
        const val KEY_PROACTIVE = "proactive.enabled"

        @Volatile
        private var instance: NexusFeatureFlags? = null

        fun getInstance(context: Context): NexusFeatureFlags {
            return instance ?: synchronized(this) {
                instance ?: NexusFeatureFlags(context.applicationContext).also { instance = it }
            }
        }
    }
}
