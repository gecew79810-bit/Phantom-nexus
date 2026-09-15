package com.pantham.nexus.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Handles:
 * microphone -> speech recognition -> transcript
 *
 * It DOES NOT generate answers itself.
 * The transcript is forwarded to the canonical Nexus message pipeline.
 */
class NexusVoiceController(
    private val context: Context,
    private val onTranscript: suspend (String) -> Unit,
    private val onErrorMessage: (String) -> Unit,
    private val onRmsChanged: ((Float) -> Unit)? = null
) {
    companion object {
        private const val TAG = "NEXUS_VOICE"
    }

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(NexusVoiceState.IDLE)
    val state: StateFlow<NexusVoiceState> = _state.asStateFlow()

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var destroyed = false

    /**
     * Returns true when RECORD_AUDIO permission is available.
     */
    fun hasMicrophonePermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "[MIC_PERMISSION] Granted: $granted")
        return granted
    }

    /**
     * Creates the recognizer only once.
     */
    private fun ensureRecognizer() {
        if (destroyed) return

        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _state.value = NexusVoiceState.ERROR
            Log.e(TAG, "[SPEECH_ERROR] Speech recognition not available on device")
            onErrorMessage("Speech recognition इस device पर उपलब्ध नहीं है।")
            return
        }

        if (speechRecognizer != null) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "[SPEECH_START] Ready for speech")
                    _state.value = NexusVoiceState.LISTENING
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "[SPEECH_START] Speech started")
                    _state.value = NexusVoiceState.LISTENING
                }

                override fun onRmsChanged(rmsdB: Float) {
                    onRmsChanged?.invoke(rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // Not needed
                }

                override fun onEndOfSpeech() {
                    Log.d(TAG, "[SPEECH_START] End of speech detected")
                    if (_state.value == NexusVoiceState.LISTENING) {
                        _state.value = NexusVoiceState.PROCESSING
                    }
                }

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO ->
                            "Microphone audio में समस्या आई।"

                        SpeechRecognizer.ERROR_CLIENT ->
                            "Speech recognizer में client error आया।"

                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Microphone permission नहीं मिली।"

                        SpeechRecognizer.ERROR_NETWORK ->
                            "Network की समस्या के कारण voice recognition fail हुआ।"

                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Speech recognition network timeout हुआ।"

                        SpeechRecognizer.ERROR_NO_MATCH ->
                            "मैं आपकी बात ठीक से समझ नहीं पाया।"

                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            "Voice recognizer अभी busy है।"

                        SpeechRecognizer.ERROR_SERVER ->
                            "Speech recognition server में समस्या आई।"

                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "मैंने कोई आवाज़ detect नहीं की।"

                        else ->
                            "Voice recognition में समस्या आई ($error)।"
                    }

                    Log.e(TAG, "[SPEECH_ERROR] Error code $error: $message")
                    _state.value = NexusVoiceState.ERROR
                    onErrorMessage(message)

                    // Small reset instead of getting permanently stuck
                    _state.value = NexusVoiceState.IDLE
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                    val spokenText = matches
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                    Log.d(TAG, "[SPEECH_RESULT] Recognized text: '$spokenText'")

                    if (spokenText.isBlank()) {
                        _state.value = NexusVoiceState.ERROR
                        onErrorMessage("मैं आपकी बात सुन नहीं पाया।")
                        _state.value = NexusVoiceState.IDLE
                        return
                    }

                    _transcript.value = spokenText
                    _state.value = NexusVoiceState.PROCESSING

                    CoroutineScope(Dispatchers.Main.immediate).launch {
                        try {
                            onTranscript(spokenText)
                        } catch (t: Throwable) {
                            Log.e(TAG, "[SPEECH_ERROR] Error in onTranscript processing", t)
                            _state.value = NexusVoiceState.ERROR
                            onErrorMessage(
                                t.message ?: "Voice request process नहीं हो सकी।"
                            )
                            _state.value = NexusVoiceState.IDLE
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        ?.firstOrNull()
                        ?.trim()

                    if (!partial.isNullOrBlank()) {
                        _transcript.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    // Reserved
                }
            })
        }
    }

    /**
     * Starts listening with safe initialization and permissions check.
     */
    fun startListening() {
        Log.d(TAG, "[VOICE_TAP] startListening() requested")

        if (destroyed) return

        if (!hasMicrophonePermission()) {
            Log.w(TAG, "[MIC_PERMISSION] Permission missing")
            onErrorMessage(
                "Microphone permission चाहिए। कृपया microphone permission allow करें।"
            )
            return
        }

        ensureRecognizer()

        val recognizer = speechRecognizer ?: return

        try {
            // Safety: stop an existing recognition session first
            recognizer.cancel()

            _transcript.value = ""
            _state.value = NexusVoiceState.LISTENING

            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    5
                )
                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )
                /*
                 * Hindi + English mixed speech is common for Nexus.
                 * Use Hindi as primary locale while allowing the
                 * recognizer/device to handle normal spoken variation.
                 */
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale("hi", "IN").toLanguageTag()
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    Locale("hi", "IN").toLanguageTag()
                )
            }

            Log.d(TAG, "[SPEECH_START] Calling startListening on SpeechRecognizer")
            recognizer.startListening(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "[SPEECH_ERROR] Failed to start recognition", t)
            _state.value = NexusVoiceState.ERROR
            onErrorMessage(
                t.message ?: "Microphone start नहीं हो पाया।"
            )
            _state.value = NexusVoiceState.IDLE
        }
    }

    /**
     * Stops and finalizes the current recognition session.
     */
    fun stopListening() {
        Log.d(TAG, "[VOICE_TAP] stopListening() requested")
        try {
            speechRecognizer?.stopListening()
        } catch (t: Throwable) {
            Log.w(TAG, "Error stopping SpeechRecognizer", t)
        }

        if (_state.value == NexusVoiceState.LISTENING) {
            _state.value = NexusVoiceState.PROCESSING
        }
    }

    /**
     * Cancels recognition immediately.
     */
    fun cancelListening() {
        Log.d(TAG, "[VOICE_TAP] cancelListening() requested")
        try {
            speechRecognizer?.cancel()
        } catch (t: Throwable) {
            Log.w(TAG, "Error cancelling SpeechRecognizer", t)
        }

        _state.value = NexusVoiceState.IDLE
        _transcript.value = ""
    }

    /**
     * Update voice state when external TTS begins.
     */
    fun markSpeaking() {
        _state.value = NexusVoiceState.SPEAKING
    }

    /**
     * Update voice state when TTS finishes.
     */
    fun markIdle() {
        _state.value = NexusVoiceState.IDLE
    }

    /**
     * Cleanup and resource release.
     */
    fun destroy() {
        if (destroyed) return
        destroyed = true

        try {
            speechRecognizer?.cancel()
        } catch (_: Throwable) {
        }

        try {
            speechRecognizer?.destroy()
        } catch (_: Throwable) {
        }

        speechRecognizer = null
        _state.value = NexusVoiceState.IDLE
    }
}
