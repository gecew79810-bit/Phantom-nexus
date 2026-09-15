package com.example.ai.intelligence

import android.content.Context
import android.content.SharedPreferences
import com.example.voice.AssistantLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PersonalizationEngine private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("nexus_personalization", Context.MODE_PRIVATE)

    private val _preferredMusicApp = MutableStateFlow(prefs.getString(KEY_PREFERRED_MUSIC_APP, "Spotify") ?: "Spotify")
    val preferredMusicApp: StateFlow<String> = _preferredMusicApp.asStateFlow()

    private val _preferredLanguage = MutableStateFlow(
        AssistantLanguage.entries.find { it.name == prefs.getString(KEY_PREFERRED_LANG, AssistantLanguage.HINDI.name) }
            ?: AssistantLanguage.HINDI
    )
    val preferredLanguage: StateFlow<AssistantLanguage> = _preferredLanguage.asStateFlow()

    fun recordMusicAppUsage(appName: String) {
        val countKey = "count_music_${appName.lowercase()}"
        val currentCount = prefs.getInt(countKey, 0) + 1
        prefs.edit().putInt(countKey, currentCount).apply()

        // Check if this app has the highest usage
        val spotifyCount = prefs.getInt("count_music_spotify", 0)
        val ytCount = prefs.getInt("count_music_youtube music", 0)
        val bestApp = if (ytCount > spotifyCount) "YouTube Music" else "Spotify"
        prefs.edit().putString(KEY_PREFERRED_MUSIC_APP, bestApp).apply()
        _preferredMusicApp.value = bestApp
    }

    fun setPreferredLanguage(language: AssistantLanguage) {
        prefs.edit().putString(KEY_PREFERRED_LANG, language.name).apply()
        _preferredLanguage.value = language
    }

    fun recordRoutineRun(routineName: String) {
        val key = "routine_run_${routineName.lowercase().replace(" ", "_")}"
        val count = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, count).apply()
    }

    fun getMostFrequentRoutines(): List<String> {
        val routines = listOf("Morning Routine", "Focus Mode", "Sleep Routine")
        return routines.sortedByDescending { routine ->
            val key = "routine_run_${routine.lowercase().replace(" ", "_")}"
            prefs.getInt(key, 0)
        }
    }

    companion object {
        private const val KEY_PREFERRED_MUSIC_APP = "pref_music_app"
        private const val KEY_PREFERRED_LANG = "pref_lang"

        @Volatile
        private var instance: PersonalizationEngine? = null

        fun getInstance(context: Context): PersonalizationEngine {
            return instance ?: synchronized(this) {
                instance ?: PersonalizationEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
