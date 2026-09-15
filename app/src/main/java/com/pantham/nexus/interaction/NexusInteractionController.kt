package com.pantham.nexus.interaction

import android.util.Log
import com.pantham.nexus.voice.NexusVoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One canonical interaction pipeline for:
 *
 * typed text
 * +
 * spoken text
 *
 * Both use the exact same Pantham Nexus processing path.
 */
class NexusInteractionController(
    private val processUserMessage: suspend (String) -> String,
    private val speakResponse: suspend (String) -> Unit,
    private val onUserMessage: (String) -> Unit,
    private val onAssistantMessage: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "NEXUS_FLOW"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _voiceState = MutableStateFlow(NexusVoiceState.IDLE)
    val voiceState: StateFlow<NexusVoiceState> = _voiceState.asStateFlow()

    /**
     * Typed input from text box or quick suggestions.
     */
    fun sendTypedMessage(text: String) {
        sendMessageInternal(
            text = text,
            shouldSpeak = false
        )
    }

    /**
     * Voice input from microphone SpeechRecognizer transcript.
     */
    fun sendVoiceTranscript(text: String) {
        sendMessageInternal(
            text = text,
            shouldSpeak = true
        )
    }

    /**
     * Central pipeline.
     *
     * IMPORTANT:
     * The user query is preserved exactly.
     * Context is appended or provided downstream, NEVER replacing the user query.
     */
    private fun sendMessageInternal(
        text: String,
        shouldSpeak: Boolean
    ) {
        val query = text.trim()

        if (query.isBlank()) {
            onError("खाली message process नहीं किया जा सकता।")
            return
        }

        scope.launch {
            try {
                Log.d(TAG, "[USER_INPUT] $query")
                onUserMessage(query)

                _voiceState.value = if (shouldSpeak) {
                    NexusVoiceState.PROCESSING
                } else {
                    NexusVoiceState.IDLE
                }

                /*
                 * CRITICAL:
                 *
                 * The ORIGINAL query goes directly into the
                 * existing Nexus user-message pipeline.
                 *
                 * Context is added downstream.
                 * Context must NEVER replace query.
                 */
                val response = processUserMessage(query)

                Log.d(TAG, "[LLM_RESPONSE] ${response.take(300)}")

                if (response.isBlank()) {
                    _voiceState.value = NexusVoiceState.ERROR
                    onError("Assistant से खाली response मिला।")
                    _voiceState.value = NexusVoiceState.IDLE
                    return@launch
                }

                onAssistantMessage(response)

                if (shouldSpeak) {
                    _voiceState.value = NexusVoiceState.SPEAKING
                    try {
                        Log.d(TAG, "[TTS_START] Speaking response")
                        speakResponse(response)
                        Log.d(TAG, "[TTS_COMPLETE] Speech playback finished or dispatched")
                    } catch (ttsError: Throwable) {
                        Log.e(TAG, "[TTS_ERROR]", ttsError)
                        onError(
                            ttsError.message ?: "Assistant response बोल नहीं पाया।"
                        )
                    }
                    _voiceState.value = NexusVoiceState.IDLE
                }

            } catch (t: Throwable) {
                Log.e(TAG, "[PROCESSING_ERROR]", t)
                _voiceState.value = NexusVoiceState.ERROR
                onError(
                    t.message ?: "Request process नहीं हो सकी।"
                )
                _voiceState.value = NexusVoiceState.IDLE
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }
}
