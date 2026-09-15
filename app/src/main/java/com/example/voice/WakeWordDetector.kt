package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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

/**
 * WakeWordResult:
 * Encapsulates the detected wake phrase and any immediate trailing voice command.
 */
data class WakeWordResult(
    val matchedWakePhrase: String,
    val trailingCommand: String = ""
)

/**
 * WakeWordDetector:
 * Dedicated zero-latency continuous wake-word detection engine for Phantom / Pantham.
 * Actively monitors for key wake phrases such as "wake panthom", "wake pantham",
 * "wake phantom", "hey pantham", "pantham", and Hindi phonetic equivalents ("वेक पैंथम", "पैंथम सुनो").
 *
 * Provides immediate acoustic chime & haptic feedback upon detection ("ek dum se sunn le")
 * and extracts trailing commands or transitions instantly into high-accuracy voice command capture.
 */
class WakeWordDetector(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onWakeWordDetected: (WakeWordResult) -> Unit
) {
    companion object {
        private const val TAG = "WakeWordDetector"
        private const val DEBOUNCE_COOLDOWN_MS = 2200L

        // Regex patterns for wake phrase matching across phonetic transliterations
        val WAKE_REGEX_PATTERNS = listOf(
            Regex("""\b(wake\s*(up)?\s*(panthom|pantham|phantom|pentom|fantom|panthum|fantum|max|macks))\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(hey|hi|hello|ok)\s*(panthom|pantham|phantom|pentom|fantom|max|macks)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(panthom|pantham|phantom|max)\s*(suno|sun|bhai|ji|kahan ho|bolo)?\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(वेक|उठो|सुनो|हेलो|हे)?\s*(अप\s*)?(पैंथम|फैंटम|पेंथम|मैक्स|फैन्टम)\b""", RegexOption.IGNORE_CASE)
        )
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isListeningForWakeWord = MutableStateFlow(false)
    val isListeningForWakeWord: StateFlow<Boolean> = _isListeningForWakeWord.asStateFlow()

    private val _isWakeWordTriggered = MutableStateFlow(false)
    val isWakeWordTriggered: StateFlow<Boolean> = _isWakeWordTriggered.asStateFlow()

    private val _lastWakeTimestamp = MutableStateFlow(0L)
    val lastWakeTimestamp: StateFlow<Long> = _lastWakeTimestamp.asStateFlow()

    private var isEnabled = false
    private var isPausedForCommand = false
    private var lastTriggerTime = 0L

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator init failed", e)
        }
    }

    /**
     * Checks whether RECORD_AUDIO permission is granted.
     */
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Enables or disables background wake-word listening.
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (enabled) {
            startWakeWordListening()
        } else {
            stopWakeWordListening()
        }
    }

    /**
     * Starts continuous wake-word listening with auto-restart on silence.
     */
    fun startWakeWordListening() {
        if (!isEnabled || isPausedForCommand) return
        if (!hasPermission()) {
            Log.w(TAG, "Cannot start wake word listener: Record audio permission missing")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer not available on device")
            return
        }

        mainHandler.post {
            try {
                destroyRecognizer()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _isListeningForWakeWord.value = true
                        }

                        override fun onBeginningOfSpeech() {}

                        override fun onRmsChanged(rmsdB: Float) {}

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            _isListeningForWakeWord.value = false
                        }

                        override fun onError(error: Int) {
                            _isListeningForWakeWord.value = false
                            if (isEnabled && !isPausedForCommand) {
                                scheduleRestartListening(delayMs = 500)
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            _isListeningForWakeWord.value = false
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            checkMatchesForWakeWord(matches)
                            if (isEnabled && !isPausedForCommand) {
                                scheduleRestartListening(delayMs = 400)
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            // Real-time zero latency check during live speech!
                            checkMatchesForWakeWord(matches, isPartial = true)
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    // Support both Hindi and English wake words seamlessly
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "en-US"))
                }

                speechRecognizer?.startListening(intent)
                _isListeningForWakeWord.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Error starting wake recognizer", e)
                scheduleRestartListening(delayMs = 1000)
            }
        }
    }

    /**
     * Pauses wake-word listener while assistant is actively listening to command or speaking.
     */
    fun pauseForCommand() {
        isPausedForCommand = true
        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        _isListeningForWakeWord.value = false
    }

    /**
     * Resumes wake-word listener once command processing or TTS has finished.
     */
    fun resumeAfterCommand() {
        isPausedForCommand = false
        if (isEnabled) {
            scheduleRestartListening(delayMs = 400)
        }
    }

    /**
     * Stops and tears down the recognizer.
     */
    fun stopWakeWordListening() {
        isEnabled = false
        isPausedForCommand = true
        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        _isListeningForWakeWord.value = false
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying wake recognizer", e)
        }
    }

    private fun scheduleRestartListening(delayMs: Long) {
        if (!isEnabled || isPausedForCommand) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (isEnabled && !isPausedForCommand) {
                startWakeWordListening()
            }
        }, delayMs)
    }

    /**
     * Analyzes speech strings against wake patterns.
     */
    private fun checkMatchesForWakeWord(candidates: List<String>?, isPartial: Boolean = false) {
        if (candidates.isNullOrEmpty()) return
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < DEBOUNCE_COOLDOWN_MS) return

        for (candidate in candidates) {
            val result = extractWakeWord(candidate)
            if (result != null) {
                lastTriggerTime = now
                _lastWakeTimestamp.value = now
                _isWakeWordTriggered.value = true

                // Instant acoustic & tactile feedback: "ek dum se sunn le"
                triggerWakeFeedback()

                // Pause wake recognizer to hand off audio hardware to command execution
                pauseForCommand()

                coroutineScope.launch(Dispatchers.Main) {
                    onWakeWordDetected(result)
                }
                break
            }
        }
    }

    /**
     * Evaluates if an utterance contains any variation of the wake word.
     */
    fun extractWakeWord(utterance: String): WakeWordResult? {
        val trimmed = utterance.trim()
        if (trimmed.isEmpty()) return null

        for (regex in WAKE_REGEX_PATTERNS) {
            val match = regex.find(trimmed)
            if (match != null) {
                val matchedText = match.value
                val trailing = trimmed.substring(match.range.last + 1)
                    .trim()
                    .removePrefix(",")
                    .removePrefix(":")
                    .trim()

                return WakeWordResult(
                    matchedWakePhrase = matchedText,
                    trailingCommand = trailing
                )
            }
        }

        // Direct containment fallback for "wake up max", "wake up phantom", "wake panthom", etc.
        val lower = trimmed.lowercase()
        val directKeywords = listOf(
            "wake up max", "wake max", "hey max", "ok max", "hello max",
            "wake up phantom", "wake phantom", "hey phantom", "ok phantom",
            "wake up panthom", "wake panthom", "hey panthom",
            "wake up pantham", "wake pantham", "hey pantham",
            "वेक अप मैक्स", "वेक मैक्स", "हे मैक्स", "मैक्स सुनो", "मैक्स",
            "वेक अप फैंटम", "वेक फैंटम", "फैंटम सुनो", "फैंटम",
            "वेक अप पैंथम", "वेक पैंथम", "पैंथम सुनो", "पैंथम",
            "phantom", "panthom", "pantham"
        )
        for (kw in directKeywords) {
            if (lower.contains(kw)) {
                val idx = lower.indexOf(kw)
                val trailing = trimmed.substring(idx + kw.length)
                    .trim()
                    .removePrefix(",")
                    .removePrefix(":")
                    .trim()
                return WakeWordResult(
                    matchedWakePhrase = kw,
                    trailingCommand = trailing
                )
            }
        }

        return null
    }

    /**
     * Plays immediate sensory feedback: high-tech sci-fi acoustic beep + haptic pulse.
     */
    private fun triggerWakeFeedback() {
        try {
            // 1. Play Instant Acoustic Wake Chime (ToneGenerator avoids external audio latency)
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 110)
        } catch (e: Exception) {
            Log.w(TAG, "Failed playing wake tone", e)
        }

        try {
            // 2. Crisp 75ms Haptic Pulse
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vm?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(75, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(75, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(75)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed executing haptic vibration", e)
        }
    }

    fun release() {
        stopWakeWordListening()
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}
    }
}
