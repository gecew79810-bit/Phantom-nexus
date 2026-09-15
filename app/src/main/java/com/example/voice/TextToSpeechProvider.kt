package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TextToSpeechProvider(
    private val context: Context,
    private val onSpeakingStateChanged: (Boolean) -> Unit = {}
) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingSpeechText: String? = null
    private var pendingLanguage: AssistantLanguage? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                _isReady.value = true
                configureLocale(Locale("hi", "IN"))
                setupListener()
                val pending = pendingSpeechText
                if (!pending.isNullOrBlank()) {
                    pendingSpeechText = null
                    val lang = pendingLanguage ?: AssistantLanguage.HINDI
                    pendingLanguage = null
                    speak(pending, lang)
                }
            } else {
                Log.e("TextToSpeech", "TTS Init failed with status: $status")
            }
        }
    }

    private fun configureLocale(locale: Locale) {
        val currentTts = tts ?: return
        val result = currentTts.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w("TextToSpeech", "Locale $locale not fully supported, falling back to US English")
            currentTts.setLanguage(Locale.US)
        }
        try {
            val availableVoices = currentTts.voices
            val bestVoice = availableVoices?.firstOrNull { v ->
                v.locale.language == locale.language &&
                (v.name.contains("neural", ignoreCase = true) || v.name.contains("natural", ignoreCase = true) || v.name.contains("hi-in", ignoreCase = true))
            } ?: availableVoices?.firstOrNull { v ->
                v.locale.language == locale.language
            }
            if (bestVoice != null) {
                currentTts.voice = bestVoice
            }
        } catch (_: Exception) {}

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
                _isSpeaking.value = false
                onSpeakingStateChanged(false)
            }

            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                onSpeakingStateChanged(false)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                onSpeakingStateChanged(false)
            }
        })
    }

    fun speak(text: String, language: AssistantLanguage = AssistantLanguage.HINDI) {
        if (!isInitialized || tts == null) {
            Log.w("TextToSpeech", "TTS not initialized yet, queueing pending speech")
            pendingSpeechText = text
            pendingLanguage = language
            return
        }

        stop()

        // Configure language before speech
        val targetLocale = when (language) {
            AssistantLanguage.HINDI -> Locale("hi", "IN")
            AssistantLanguage.HINGLISH -> Locale("hi", "IN")
            AssistantLanguage.ENGLISH -> Locale("en", "IN")
        }
        configureLocale(targetLocale)

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "max_utterance_${System.currentTimeMillis()}")
        }

        _isSpeaking.value = true
        onSpeakingStateChanged(true)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "max_speech")
    }

    fun stop() {
        try {
            tts?.stop()
            _isSpeaking.value = false
            onSpeakingStateChanged(false)
        } catch (e: Exception) {
            Log.w("TextToSpeech", "Error stopping TTS", e)
        }
    }

    fun destroy() {
        try {
            stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            Log.w("TextToSpeech", "Error shutting down TTS", e)
        }
    }
}
