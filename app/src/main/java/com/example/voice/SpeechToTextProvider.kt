package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpeechToTextProvider(
    private val context: Context,
    private val onFinalText: (String) -> Unit,
    private val onPartialText: (String) -> Unit = {},
    private val onRmsChangedCallback: (Float) -> Unit = {},
    private val onListeningStateChanged: (Boolean) -> Unit = {},
    private val onErrorCallback: (String) -> Unit = {}
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(language: AssistantLanguage = AssistantLanguage.HINDI) {
        stopListening()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            val err = "Speech recognition is not available on this device."
            _errorMessage.value = err
            _isListening.value = false
            onListeningStateChanged(false)
            onErrorCallback(err)
            return
        }

        try {
            speechRecognizer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }.apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _errorMessage.value = null
                        onListeningStateChanged(true)
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                        onListeningStateChanged(true)
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        onRmsChangedCallback(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        onListeningStateChanged(false)
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        onListeningStateChanged(false)
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error for STT"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                            else -> "Speech recognition error ($error)"
                        }
                        Log.w("SpeechToText", "onError: $msg")
                        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            _errorMessage.value = msg
                            onErrorCallback(msg)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        onListeningStateChanged(false)
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenText = matches?.firstOrNull()?.trim() ?: ""
                        if (spokenText.isNotEmpty()) {
                            onFinalText(spokenText)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            onPartialText(text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.speechCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.speechCode)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            Log.e("SpeechToText", "Failed to start listening", e)
            val msg = "Microphone error: ${e.message}"
            _errorMessage.value = msg
            _isListening.value = false
            onListeningStateChanged(false)
            onErrorCallback(msg)
        }
    }

    fun stopListening() {
        try {
            _isListening.value = false
            onListeningStateChanged(false)
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.w("SpeechToText", "Error stopping recognizer", e)
        }
    }

    fun destroy() {
        stopListening()
    }
}
