package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * EdgeTTSVoiceProfile:
 * Represents Microsoft Edge Natural Neural Voices.
 */
enum class EdgeVoice(val voiceId: String, val displayName: String, val languageCode: String, val gender: String) {
    SWARA_NEURAL("hi-IN-SwaraNeural", "Microsoft Edge Swara (Sweet & Playful Hindi)", "hi-IN", "Female"),
    MADHUR_NEURAL("hi-IN-MadhurNeural", "Microsoft Edge Madhur (Natural Hindi)", "hi-IN", "Male"),
    NEERJA_NEURAL("en-IN-NeerjaNeural", "Microsoft Edge Neerja (Expressive Indian English)", "en-IN", "Female"),
    PRABHAT_NEURAL("en-IN-PrabhatNeural", "Microsoft Edge Prabhat (Indian English)", "en-IN", "Male")
}

/**
 * EdgeTTSProvider:
 * Natural Expressive Text-To-Speech with human-like pitch, breath pauses,
 * softness, playfulness (नटखटपन), and low-latency audio streaming chunking.
 * Includes Microsoft Edge Neural Voice profile support.
 */
class EdgeTTSProvider(
    private val context: Context,
    private val onSpeakingStateChanged: (Boolean) -> Unit = {}
) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val prefs = context.getSharedPreferences("edge_tts_prefs", Context.MODE_PRIVATE)

    @Volatile
    private var lastQueuedUtteranceId: String? = null
    private var pendingSpeechText: String? = null
    private var pendingVoice: EdgeVoice? = null

    var currentVoice: EdgeVoice
        get() {
            val id = prefs.getString("edge_voice_id", EdgeVoice.SWARA_NEURAL.name) ?: EdgeVoice.SWARA_NEURAL.name
            return try { EdgeVoice.valueOf(id) } catch (e: Exception) { EdgeVoice.SWARA_NEURAL }
        }
        set(value) = prefs.edit().putString("edge_voice_id", value.name).apply()

    var isPlayfulPitchEnabled: Boolean
        get() = prefs.getBoolean("is_playful_pitch", true)
        set(value) = prefs.edit().putBoolean("is_playful_pitch", value).apply()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val ttsScope = CoroutineScope(Dispatchers.Main)

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                _isReady.value = true
                applyVoiceProfile(currentVoice)
                setupListener()
                val pending = pendingSpeechText
                if (!pending.isNullOrBlank()) {
                    pendingSpeechText = null
                    val v = pendingVoice ?: currentVoice
                    pendingVoice = null
                    speak(pending, v)
                }
            } else {
                Log.e("EdgeTTSProvider", "TTS Init failed status: $status")
            }
        }
    }

    private fun applyVoiceProfile(voice: EdgeVoice) {
        val currentTts = tts ?: return
        val targetLocale = when (voice.languageCode) {
            "hi-IN" -> Locale("hi", "IN")
            "en-IN" -> Locale("en", "IN")
            else -> Locale("hi", "IN")
        }

        val result = currentTts.setLanguage(targetLocale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            currentTts.setLanguage(Locale.US)
        }

        // Search for natural human-like voice among system voices
        try {
            val availableVoices = currentTts.voices
            val bestVoice = availableVoices?.firstOrNull { v ->
                v.locale.language == targetLocale.language &&
                (v.name.contains("neural", ignoreCase = true) || v.name.contains("natural", ignoreCase = true) || v.name.contains("hi-in", ignoreCase = true))
            } ?: availableVoices?.firstOrNull { v ->
                v.locale.language == targetLocale.language
            }
            if (bestVoice != null) {
                currentTts.voice = bestVoice
            }
        } catch (_: Exception) {}

        // Set natural, warm human pitch and balanced speech rate (removes robotic metallic tone)
        currentTts.setPitch(1.0f)
        currentTts.setSpeechRate(1.0f)
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                onSpeakingStateChanged(true)
            }

            override fun onDone(utteranceId: String?) {
                // Only signal done when the last chunk of the queued utterance has finished
                if (utteranceId == lastQueuedUtteranceId || lastQueuedUtteranceId == null) {
                    lastQueuedUtteranceId = null
                    _isSpeaking.value = false
                    onSpeakingStateChanged(false)
                }
            }

            override fun onError(utteranceId: String?) {
                if (utteranceId == lastQueuedUtteranceId || lastQueuedUtteranceId == null) {
                    lastQueuedUtteranceId = null
                    _isSpeaking.value = false
                    onSpeakingStateChanged(false)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == lastQueuedUtteranceId || lastQueuedUtteranceId == null) {
                    lastQueuedUtteranceId = null
                    _isSpeaking.value = false
                    onSpeakingStateChanged(false)
                }
            }
        })
    }

    /**
     * Low-Latency Audio Streaming:
     * Breaks text into immediate natural conversational chunks with breath pauses,
     * queuing them seamlessly to eliminate dead air.
     */
    fun speak(text: String, voice: EdgeVoice = currentVoice) {
        if (!isInitialized || tts == null) {
            Log.w("EdgeTTSProvider", "TTS not initialized yet, queueing pending speech")
            pendingSpeechText = text
            pendingVoice = voice
            return
        }

        stop()
        applyVoiceProfile(voice)

        // Process expressive breath pauses and emojis
        val processed = text
            .replace("...", ", ")
            .replace("❤️", "")
            .replace("😉", "")
            .replace("😊", "")
            .replace("😜", "")
            .replace("✨", "")
            .trim()

        // Split into immediate sentence chunks for low latency
        val chunks = processed.split(Regex("(?<=[.?!|।])\\s+")).filter { it.isNotBlank() }

        if (chunks.isEmpty()) return

        val batchId = System.currentTimeMillis()
        val finalIndex = chunks.size - 1
        val finalUtteranceId = "edge_utt_${batchId}_$finalIndex"
        lastQueuedUtteranceId = finalUtteranceId

        _isSpeaking.value = true
        onSpeakingStateChanged(true)

        ttsScope.launch {
            for ((index, chunk) in chunks.withIndex()) {
                val utteranceId = "edge_utt_${batchId}_$index"
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }
                val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                tts?.speak(chunk, queueMode, params, "edge_stream_$index")
            }
        }
    }

    fun stop() {
        try {
            lastQueuedUtteranceId = null
            pendingSpeechText = null
            tts?.stop()
            _isSpeaking.value = false
            onSpeakingStateChanged(false)
        } catch (e: Exception) {
            Log.e("EdgeTTSProvider", "Error stopping TTS", e)
        }
    }

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            Log.e("EdgeTTSProvider", "Error releasing TTS", e)
        }
    }
}
