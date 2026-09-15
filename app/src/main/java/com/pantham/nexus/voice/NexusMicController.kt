package com.pantham.nexus.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MicState {
    IDLE,
    STARTING,
    LISTENING,
    PROCESSING,
    ERROR
}

/**
 * Pantham Nexus - Android Speech Recognition Controller
 *
 * Responsibilities:
 * - Start/stop/cancel Android SpeechRecognizer
 * - Hindi -> Indian English -> US English fallback
 * - Protect against stale recognizer callbacks
 * - Protect against recognizer startup hanging forever
 * - Keep all SpeechRecognizer operations on Main thread
 * - Expose mic state and partial transcript
 * - Forward final transcript to existing command pipeline
 *
 * IMPORTANT:
 * This class does NOT handle Gemini.
 * This class does NOT handle TTS.
 * This class does NOT handle UI directly.
 * It only handles microphone speech recognition.
 */
class NexusMicController(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onPartialResult: (String) -> Unit = {},
    private val onErrorMessage: (String) -> Unit = {}
) {

    /**
     * Compatibility constructor for existing callers.
     *
     * Existing code using:
     *
     * NexusMicController(
     *     context,
     *     onTextRecognized,
     *     onError
     * )
     *
     * will continue to work.
     */
    constructor(
        context: Context,
        onTextRecognized: (String) -> Unit,
        onError: (String) -> Unit
    ) : this(
        context = context,
        onResult = onTextRecognized,
        onPartialResult = {},
        onErrorMessage = onError
    )

    companion object {

        private const val TAG = "NEXUS_MIC"

        // -------------------------------------------------------------
        // LANGUAGE FALLBACK
        // -------------------------------------------------------------

        private const val LANG_HI_IN = "hi-IN"
        private const val LANG_EN_IN = "en-IN"
        private const val LANG_EN_US = "en-US"

        /**
         * Startup watchdog.
         *
         * If SpeechRecognizer does not call either:
         * onReadyForSpeech()
         * OR
         * onError()
         *
         * within this time, we consider startup stuck.
         */
        private const val START_TIMEOUT_MS = 8_000L

        /**
         * How long an error remains in ERROR state.
         */
        private const val ERROR_DISPLAY_MS = 2_500L

        /**
         * Android SpeechRecognizer error constants.
         *
         * These constants are available on newer Android SDKs.
         * Keeping numeric fallbacks makes this controller safer
         * when compiling against different SDK levels.
         */
        private const val ERROR_LANGUAGE_NOT_SUPPORTED_CODE = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE_CODE = 13

        /**
         * Maximum number of language attempts in one microphone turn.
         *
         * hi-IN
         * en-IN
         * en-US
         */
        private const val MAX_LANGUAGE_ATTEMPTS = 3
    }

    // -------------------------------------------------------------
    // CONTEXT / MAIN THREAD
    // -------------------------------------------------------------

    private val appContext = context.applicationContext

    private val mainHandler = Handler(Looper.getMainLooper())

    // -------------------------------------------------------------
    // PUBLIC STATE
    // -------------------------------------------------------------

    private val _state = MutableStateFlow(MicState.IDLE)

    val state: StateFlow<MicState> = _state.asStateFlow()

    private val _partialText = MutableStateFlow("")

    val partialText: StateFlow<String> = _partialText.asStateFlow()

    // -------------------------------------------------------------
    // INTERNAL STATE
    // -------------------------------------------------------------

    private var recognizer: SpeechRecognizer? = null

    private var isListening = false

    /**
     * Every recognition activation gets a unique session ID.
     *
     * Old SpeechRecognizer callbacks can sometimes arrive after
     * cancel/destroy. The session ID prevents those callbacks
     * from modifying the new session.
     */
    private var sessionId = 0L

    /**
     * Startup watchdog runnable.
     */
    private var startTimeoutRunnable: Runnable? = null

    /**
     * Current recognition language.
     *
     * New microphone turns start with Hindi by default.
     */
    private var currentRecognitionLanguage = LANG_HI_IN

    /**
     * Last language that successfully produced a final result.
     *
     * We keep this during the lifetime of this controller.
     *
     * Initially Hindi.
     */
    private var sessionWorkingLanguage = LANG_HI_IN

    /**
     * Languages attempted during the current microphone activation.
     *
     * Prevents:
     * hi-IN -> en-IN -> hi-IN -> ...
     */
    private val attemptedLanguagesThisTurn = mutableSetOf<String>()

    /**
     * Indicates that the current turn is intentionally being retried
     * because of a language availability problem.
     *
     * This prevents the UI from treating the fallback transition
     * as a fatal microphone error.
     */
    private var fallbackInProgress = false

    /**
     * Prevent multiple final-result callbacks for the same session.
     */
    private var resultDeliveredForSession = false

    // -------------------------------------------------------------
    // PUBLIC API
    // -------------------------------------------------------------

    /**
     * Check RECORD_AUDIO permission.
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Compatibility initialization method.
     *
     * SpeechRecognizer is deliberately NOT created here.
     *
     * It is created only when start() is called.
     */
    fun initialize() {
        Log.d(TAG, "initialize() called")
        Log.d(TAG, "SpeechRecognizer will be created lazily by start()")
    }

    /**
     * Start microphone recognition.
     *
     * Can safely be called from any thread.
     * Actual SpeechRecognizer work is always dispatched to Main.
     */
    fun start() {
        Log.d(
            TAG,
            "start() requested from thread=${Thread.currentThread().name}"
        )

        mainHandler.post {
            startOnMainThread()
        }
    }

    /**
     * Stop listening.
     *
     * This invalidates the current recognition session.
     */
    fun stop() {
        mainHandler.post {
            checkMainThread()

            Log.d(TAG, "stop() called")

            invalidateCurrentSession()

            cancelStartTimeout()

            cancelRecognizerOnly()

            _partialText.value = ""

            _state.value = MicState.IDLE

            Log.d(TAG, "STATE = IDLE")
        }
    }

    /**
     * Cancel current recognition.
     *
     * Similar to stop(), but explicitly cancels the recognizer.
     */
    fun cancel() {
        mainHandler.post {
            checkMainThread()

            Log.d(TAG, "cancel() called")

            invalidateCurrentSession()

            cancelStartTimeout()

            try {
                recognizer?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "recognizer.cancel() failed", e)
            }

            isListening = false
            fallbackInProgress = false

            _partialText.value = ""

            _state.value = MicState.IDLE

            Log.d(TAG, "STATE = IDLE")
        }
    }

    /**
     * Release all SpeechRecognizer resources.
     */
    fun release() {
        mainHandler.post {
            checkMainThread()

            Log.d(TAG, "release() called")

            invalidateCurrentSession()

            cancelStartTimeout()

            destroyRecognizerInternal()

            isListening = false
            fallbackInProgress = false

            _partialText.value = ""

            _state.value = MicState.IDLE

            Log.d(TAG, "SpeechRecognizer released")
        }
    }

    /**
     * Compatibility alias.
     */
    fun destroy() {
        release()
    }

    // -------------------------------------------------------------
    // START
    // -------------------------------------------------------------

    private fun startOnMainThread() {

        checkMainThread()

        Log.d(TAG, "========================================")
        Log.d(TAG, "          NEXUS MIC START")
        Log.d(TAG, "========================================")

        /**
         * If a previous recognizer is still active, kill it first.
         */
        if (
            _state.value == MicState.STARTING ||
            _state.value == MicState.LISTENING ||
            _state.value == MicState.PROCESSING
        ) {
            Log.d(TAG, "Existing recognition session detected")
            Log.d(TAG, "Cancelling previous session before starting new one")

            invalidateCurrentSession()

            cancelStartTimeout()

            cancelRecognizerOnly()

            destroyRecognizerInternal()
        }

        /**
         * New microphone activation.
         */
        sessionId++

        val newSessionId = sessionId

        Log.d(TAG, "New microphone session = $newSessionId")

        attemptedLanguagesThisTurn.clear()

        fallbackInProgress = false

        resultDeliveredForSession = false

        /**
         * Start each fresh turn with the last known working language.
         *
         * First launch = hi-IN.
         */
        currentRecognitionLanguage = sessionWorkingLanguage

        Log.d(
            TAG,
            "Starting language = $currentRecognitionLanguage"
        )

        _partialText.value = ""

        startRecognitionForSession(newSessionId)
    }

    // -------------------------------------------------------------
    // START RECOGNITION
    // -------------------------------------------------------------

    private fun startRecognitionForSession(
        recognitionSessionId: Long
    ) {

        checkMainThread()

        if (!isCurrentSession(recognitionSessionId)) {
            Log.d(
                TAG,
                "startRecognitionForSession() ignored because session is stale"
            )
            return
        }

        /**
         * Track this language.
         */
        attemptedLanguagesThisTurn.add(currentRecognitionLanguage)

        Log.d(
            TAG,
            "----------------------------------------"
        )

        Log.d(
            TAG,
            "Starting SpeechRecognizer"
        )

        Log.d(
            TAG,
            "Session ID = $recognitionSessionId"
        )

        Log.d(
            TAG,
            "Language = $currentRecognitionLanguage"
        )

        Log.d(
            TAG,
            "Attempted languages = $attemptedLanguagesThisTurn"
        )

        Log.d(
            TAG,
            "----------------------------------------"
        )

        cancelStartTimeout()

        // ---------------------------------------------------------
        // PERMISSION
        // ---------------------------------------------------------

        if (!hasPermission()) {

            Log.e(
                TAG,
                "RECORD_AUDIO permission NOT granted"
            )

            showFatalError(
                "माइक्रोफ़ोन परमिशन नहीं मिली। कृपया Microphone permission Allow करें।"
            )

            return
        }

        // ---------------------------------------------------------
        // SPEECH SERVICE AVAILABILITY
        // ---------------------------------------------------------

        val speechAvailable = try {

            SpeechRecognizer.isRecognitionAvailable(
                appContext
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "isRecognitionAvailable() failed",
                e
            )

            false
        }

        Log.d(
            TAG,
            "SpeechRecognizer available = $speechAvailable"
        )

        if (!speechAvailable) {

            showFatalError(
                "इस डिवाइस पर Speech Recognition service उपलब्ध नहीं है।"
            )

            return
        }

        // ---------------------------------------------------------
        // STATE
        // ---------------------------------------------------------

        _state.value = MicState.STARTING

        isListening = false

        Log.d(TAG, "STATE = STARTING")

        // ---------------------------------------------------------
        // CREATE RECOGNIZER
        // ---------------------------------------------------------

        destroyRecognizerInternal()

        val speechRecognizer = try {

            Log.d(
                TAG,
                "Creating SpeechRecognizer..."
            )

            SpeechRecognizer.createSpeechRecognizer(
                appContext
            )

        } catch (e: SecurityException) {

            Log.e(
                TAG,
                "SecurityException creating SpeechRecognizer",
                e
            )

            showFatalError(
                "माइक्रोफ़ोन access की अनुमति नहीं मिली।"
            )

            return

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Exception creating SpeechRecognizer",
                e
            )

            showFatalError(
                "Speech recognizer शुरू नहीं हो सका।"
            )

            return
        }

        recognizer = speechRecognizer

        Log.d(
            TAG,
            "SpeechRecognizer CREATED"
        )

        // ---------------------------------------------------------
        // ATTACH LISTENER
        // ---------------------------------------------------------

        try {

            speechRecognizer.setRecognitionListener(
                createRecognitionListener(
                    recognitionSessionId
                )
            )

            Log.d(
                TAG,
                "RecognitionListener ATTACHED"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "setRecognitionListener() failed",
                e
            )

            destroyRecognizerInternal()

            showFatalError(
                "Speech listener initialize नहीं हो सका।"
            )

            return
        }

        // ---------------------------------------------------------
        // INTENT
        // ---------------------------------------------------------

        val recognitionIntent =
            buildRecognizerIntent(
                currentRecognitionLanguage
            )

        // ---------------------------------------------------------
        // START LISTENING
        // ---------------------------------------------------------

        try {

            Log.d(
                TAG,
                "Calling startListening()"
            )

            Log.d(
                TAG,
                "Language = $currentRecognitionLanguage"
            )

            speechRecognizer.startListening(
                recognitionIntent
            )

            Log.d(
                TAG,
                "startListening() returned successfully"
            )

            /**
             * Watchdog.
             *
             * If Android SpeechRecognizer does not send
             * onReadyForSpeech() or onError(), do not leave
             * the app stuck forever at STARTING.
             */
            startTimeoutRunnable = Runnable {

                if (
                    isCurrentSession(recognitionSessionId) &&
                    _state.value == MicState.STARTING
                ) {

                    Log.e(
                        TAG,
                        "START TIMEOUT after ${START_TIMEOUT_MS}ms"
                    )

                    /**
                     * Treat this as a speech-service startup failure.
                     */
                    showFatalError(
                        "Speech service ने response नहीं दिया। Google Speech service और microphone settings check करें।"
                    )

                    cancelRecognizerOnly()

                    destroyRecognizerInternal()
                }
            }

            mainHandler.postDelayed(
                startTimeoutRunnable!!,
                START_TIMEOUT_MS
            )

        } catch (e: SecurityException) {

            Log.e(
                TAG,
                "SecurityException from startListening()",
                e
            )

            showFatalError(
                "Microphone permission के कारण listening शुरू नहीं हो सकी।"
            )

            destroyRecognizerInternal()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Exception from startListening()",
                e
            )

            showFatalError(
                "माइक शुरू नहीं हो सका: ${e.message ?: "unknown error"}"
            )

            destroyRecognizerInternal()
        }
    }

    // -------------------------------------------------------------
    // RECOGNIZER INTENT
    // -------------------------------------------------------------

    private fun buildRecognizerIntent(
        language: String
    ): Intent {

        return Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                language
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                language
            )

            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                true
            )

            putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                3
            )

            /**
             * These values make the interaction feel natural
             * for a conversational assistant.
             */
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                2_000L
            )

            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                1_500L
            )

            putExtra(
                RecognizerIntent.EXTRA_CALLING_PACKAGE,
                appContext.packageName
            )
        }
    }

    // -------------------------------------------------------------
    // LISTENER
    // -------------------------------------------------------------

    private fun createRecognitionListener(
        listenerSessionId: Long
    ): RecognitionListener {

        return object : RecognitionListener {

            // -----------------------------------------------------
            // READY
            // -----------------------------------------------------

            override fun onReadyForSpeech(
                params: Bundle?
            ) {

                if (!isCurrentSession(listenerSessionId)) {

                    Log.d(
                        TAG,
                        "Ignoring stale onReadyForSpeech()"
                    )

                    return
                }

                cancelStartTimeout()

                isListening = true

                fallbackInProgress = false

                _state.value = MicState.LISTENING

                Log.d(
                    TAG,
                    "========================================"
                )

                Log.d(
                    TAG,
                    ">>> onReadyForSpeech"
                )

                Log.d(
                    TAG,
                    "Language = $currentRecognitionLanguage"
                )

                Log.d(
                    TAG,
                    "STATE = LISTENING"
                )

                Log.d(
                    TAG,
                    "========================================"
                )
            }

            // -----------------------------------------------------
            // BEGINNING OF SPEECH
            // -----------------------------------------------------

            override fun onBeginningOfSpeech() {

                if (!isCurrentSession(listenerSessionId)) {
                    return
                }

                cancelStartTimeout()

                isListening = true

                _state.value = MicState.LISTENING

                Log.d(
                    TAG,
                    ">>> onBeginningOfSpeech"
                )
            }

            // -----------------------------------------------------
            // RMS
            // -----------------------------------------------------

            override fun onRmsChanged(
                rmsdB: Float
            ) {

                if (!isCurrentSession(listenerSessionId)) {
                    return
                }

                Log.d(
                    TAG,
                    "RMS = $rmsdB"
                )
            }

            // -----------------------------------------------------
            // BUFFER
            // -----------------------------------------------------

            override fun onBufferReceived(
                buffer: ByteArray?
            ) {

                if (!isCurrentSession(listenerSessionId)) {
                    return
                }

                Log.d(
                    TAG,
                    "onBufferReceived size=${buffer?.size ?: 0}"
                )
            }

            // -----------------------------------------------------
            // END OF SPEECH
            // -----------------------------------------------------

            override fun onEndOfSpeech() {

                if (!isCurrentSession(listenerSessionId)) {
                    return
                }

                cancelStartTimeout()

                isListening = false

                _state.value = MicState.PROCESSING

                Log.d(
                    TAG,
                    ">>> onEndOfSpeech"
                )

                Log.d(
                    TAG,
                    "STATE = PROCESSING"
                )
            }

            // -----------------------------------------------------
            // PARTIAL RESULTS
            // -----------------------------------------------------

            override fun onPartialResults(
                partialResults: Bundle?
            ) {

                if (!isCurrentSession(listenerSessionId)) {
                    return
                }

                val matches =
                    partialResults?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                val text =
                    matches
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                if (text.isNotBlank()) {

                    Log.d(
                        TAG,
                        ">>> onPartialResults: \"$text\""
                    )

                    _partialText.value = text

                    try {

                        onPartialResult(text)

                    } catch (e: Exception) {

                        Log.e(
                            TAG,
                            "onPartialResult() threw exception",
                            e
                        )
                    }
                }
            }

            // -----------------------------------------------------
            // FINAL RESULTS
            // -----------------------------------------------------

            override fun onResults(
                results: Bundle?
            ) {

                if (!isCurrentSession(listenerSessionId)) {

                    Log.d(
                        TAG,
                        "Ignoring stale onResults()"
                    )

                    return
                }

                cancelStartTimeout()

                isListening = false

                val matches =
                    results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                val finalText =
                    matches
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                Log.d(
                    TAG,
                    "========================================"
                )

                Log.d(
                    TAG,
                    ">>> onResults"
                )

                Log.d(
                    TAG,
                    "Language = $currentRecognitionLanguage"
                )

                Log.d(
                    TAG,
                    "Matches = $matches"
                )

                Log.d(
                    TAG,
                    "Final text = \"$finalText\""
                )

                Log.d(
                    TAG,
                    "========================================"
                )

                _state.value = MicState.PROCESSING

                // -------------------------------------------------
                // EMPTY RESULT
                // -------------------------------------------------

                if (finalText.isBlank()) {

                    Log.e(
                        TAG,
                        "Final speech result is EMPTY"
                    )

                    showRecoverableError(
                        "कुछ सुनाई नहीं दिया, दोबारा बोलिए।"
                    )

                    return
                }

                // -------------------------------------------------
                // PREVENT DUPLICATE DELIVERY
                // -------------------------------------------------

                if (resultDeliveredForSession) {

                    Log.w(
                        TAG,
                        "Duplicate final result ignored: \"$finalText\""
                    )

                    return
                }

                resultDeliveredForSession = true

                // -------------------------------------------------
                // SAVE WORKING LANGUAGE
                // -------------------------------------------------

                sessionWorkingLanguage =
                    currentRecognitionLanguage

                Log.d(
                    TAG,
                    "Saved working language = $sessionWorkingLanguage"
                )

                // -------------------------------------------------
                // UPDATE PARTIAL TEXT WITH FINAL TEXT
                // -------------------------------------------------

                _partialText.value = finalText

                // -------------------------------------------------
                // FORWARD TO EXISTING COMMAND PIPELINE
                // -------------------------------------------------

                try {

                    Log.d(
                        TAG,
                        "Forwarding transcript to command pipeline"
                    )

                    onResult(finalText)

                    Log.d(
                        TAG,
                        "Transcript forwarded successfully"
                    )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "onResult() threw exception",
                        e
                    )

                    try {

                        onErrorMessage(
                            "कमांड प्रोसेस नहीं हो सका।"
                        )

                    } catch (ignored: Exception) {
                        Log.e(
                            TAG,
                            "onErrorMessage() failed",
                            ignored
                        )
                    }
                }

                // -------------------------------------------------
                // RETURN TO IDLE
                // -------------------------------------------------

                mainHandler.post {

                    if (
                        isCurrentSession(listenerSessionId) &&
                        _state.value == MicState.PROCESSING
                    ) {

                        _state.value = MicState.IDLE

                        isListening = false

                        Log.d(
                            TAG,
                            "STATE = IDLE"
                        )
                    }
                }
            }

            // -----------------------------------------------------
            // ERROR
            // -----------------------------------------------------

            override fun onError(
                error: Int
            ) {

                if (!isCurrentSession(listenerSessionId)) {

                    Log.d(
                        TAG,
                        "Ignoring stale onError() code=$error"
                    )

                    return
                }

                cancelStartTimeout()

                isListening = false

                Log.e(
                    TAG,
                    "========================================"
                )

                Log.e(
                    TAG,
                    "Speech recognition ERROR"
                )

                Log.e(
                    TAG,
                    "Code = $error"
                )

                Log.e(
                    TAG,
                    "Name = ${errorName(error)}"
                )

                Log.e(
                    TAG,
                    "Language = $currentRecognitionLanguage"
                )

                Log.e(
                    TAG,
                    "========================================"
                )

                // -------------------------------------------------
                // ERROR 12 / 13
                // LANGUAGE PROBLEM
                // -------------------------------------------------

                if (
                    error == ERROR_LANGUAGE_NOT_SUPPORTED_CODE ||
                    error == ERROR_LANGUAGE_UNAVAILABLE_CODE
                ) {

                    Log.e(
                        TAG,
                        "Language error detected"
                    )

                    Log.e(
                        TAG,
                        "Current language = $currentRecognitionLanguage"
                    )

                    val nextLanguage =
                        getNextFallbackLanguage(
                            currentRecognitionLanguage
                        )

                    if (
                        nextLanguage != null &&
                        !attemptedLanguagesThisTurn.contains(
                            nextLanguage
                        ) &&
                        attemptedLanguagesThisTurn.size <
                        MAX_LANGUAGE_ATTEMPTS
                    ) {

                        Log.w(
                            TAG,
                            "LANGUAGE FALLBACK:"
                        )

                        Log.w(
                            TAG,
                            "$currentRecognitionLanguage -> $nextLanguage"
                        )

                        fallbackInProgress = true

                        /**
                         * IMPORTANT:
                         *
                         * Do not show Error 12/13 as a fatal
                         * microphone error.
                         *
                         * We are intentionally retrying.
                         */
                        try {

                            onErrorMessage(
                                when (nextLanguage) {

                                    LANG_EN_IN ->
                                        "हिंदी speech model उपलब्ध नहीं है। English recognition try कर रहा हूँ..."

                                    LANG_EN_US ->
                                        "Indian English model उपलब्ध नहीं है। English (US) recognition try कर रहा हूँ..."

                                    else ->
                                        "दूसरी speech language try कर रहा हूँ..."
                                }
                            )

                        } catch (e: Exception) {

                            Log.e(
                                TAG,
                                "Fallback status callback failed",
                                e
                            )
                        }

                        /**
                         * Invalidate the old recognizer.
                         *
                         * Do NOT increment sessionId here manually.
                         * The next startSession will create the new
                         * valid session.
                         */
                        destroyRecognizerInternal()

                        currentRecognitionLanguage =
                            nextLanguage

                        /**
                         * Create a fresh session.
                         */
                        mainHandler.post {

                            if (!fallbackInProgress) {
                                return@post
                            }

                            sessionId++

                            val fallbackSessionId =
                                sessionId

                            resultDeliveredForSession = false

                            Log.d(
                                TAG,
                                "Starting fallback session=$fallbackSessionId"
                            )

                            startRecognitionForSession(
                                fallbackSessionId
                            )
                        }

                        return
                    }

                    // -------------------------------------------------
                    // ALL LANGUAGE FALLBACKS EXHAUSTED
                    // -------------------------------------------------

                    Log.e(
                        TAG,
                        "All speech language fallbacks exhausted"
                    )

                    fallbackInProgress = false

                    showRecoverableError(
                        "Speech language model उपलब्ध नहीं है। Google Speech Services में Hindi या English language download करें।"
                    )

                    return
                }

                // -------------------------------------------------
                // NORMAL ERRORS
                // -------------------------------------------------

                fallbackInProgress = false

                val readableName =
                    errorName(error)

                val message =
                    mapErrorToMessage(error)

                Log.e(
                    TAG,
                    "Normal speech error:"
                )

                Log.e(
                    TAG,
                    "Code = $error"
                )

                Log.e(
                    TAG,
                    "Name = $readableName"
                )

                Log.e(
                    TAG,
                    "Message = $message"
                )

                showRecoverableError(message)
            }

            // -----------------------------------------------------
            // EVENT
            // -----------------------------------------------------

            override fun onEvent(
                eventType: Int,
                params: Bundle?
            ) {

                if (!isCurrentSession(listenerSessionId)) {
                    return
                }

                Log.d(
                    TAG,
                    ">>> onEvent eventType=$eventType"
                )
            }
        }
    }

    // -------------------------------------------------------------
    // LANGUAGE FALLBACK
    // -------------------------------------------------------------

    /**
     * Fallback chain:
     *
     * hi-IN -> en-IN -> en-US
     */
    private fun getNextFallbackLanguage(
        currentLanguage: String
    ): String? {

        return when (currentLanguage) {

            LANG_HI_IN ->
                LANG_EN_IN

            LANG_EN_IN ->
                LANG_EN_US

            LANG_EN_US ->
                null

            else ->
                LANG_EN_IN
        }
    }

    // -------------------------------------------------------------
    // SESSION
    // -------------------------------------------------------------

    /**
     * Invalidate the currently active listener session.
     *
     * Old callbacks will fail isCurrentSession().
     */
    private fun invalidateCurrentSession() {

        sessionId++

        Log.d(
            TAG,
            "Current session invalidated. New sessionId=$sessionId"
        )
    }

    private fun isCurrentSession(
        listenerSessionId: Long
    ): Boolean {

        return listenerSessionId == sessionId
    }

    // -------------------------------------------------------------
    // RECOGNIZER CONTROL
    // -------------------------------------------------------------

    /**
     * Cancel current recognition without destroying object.
     */
    private fun cancelRecognizerOnly() {

        checkMainThread()

        try {

            recognizer?.cancel()

            Log.d(
                TAG,
                "recognizer.cancel() executed"
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "recognizer.cancel() failed",
                e
            )
        }

        isListening = false
    }

    /**
     * Completely destroy current SpeechRecognizer.
     *
     * This is intentionally called before creating a fallback
     * recognizer to avoid having two active recognition services.
     */
    private fun destroyRecognizerInternal() {

        checkMainThread()

        val oldRecognizer =
            recognizer

        recognizer = null

        if (oldRecognizer == null) {
            return
        }

        try {

            oldRecognizer.cancel()

        } catch (e: Exception) {

            Log.w(
                TAG,
                "cancel() during destroy failed",
                e
            )
        }

        try {

            oldRecognizer.destroy()

            Log.d(
                TAG,
                "SpeechRecognizer DESTROYED"
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "SpeechRecognizer.destroy() failed",
                e
            )
        }

        isListening = false
    }

    // -------------------------------------------------------------
    // TIMEOUT
    // -------------------------------------------------------------

    private fun cancelStartTimeout() {

        val runnable =
            startTimeoutRunnable
                ?: return

        mainHandler.removeCallbacks(
            runnable
        )

        startTimeoutRunnable = null

        Log.d(
            TAG,
            "Startup watchdog cancelled"
        )
    }

    // -------------------------------------------------------------
    // ERROR UI
    // -------------------------------------------------------------

    /**
     * Fatal error.
     *
     * Used when recognition cannot be started at all.
     */
    private fun showFatalError(
        message: String
    ) {

        checkMainThread()

        cancelStartTimeout()

        fallbackInProgress = false

        isListening = false

        _state.value = MicState.ERROR

        Log.e(
            TAG,
            "FATAL ERROR: $message"
        )

        try {

            onErrorMessage(message)

        } catch (e: Exception) {

            Log.e(
                TAG,
                "onErrorMessage() threw exception",
                e
            )
        }

        mainHandler.postDelayed({

            if (_state.value == MicState.ERROR) {

                _state.value = MicState.IDLE

                Log.d(
                    TAG,
                    "STATE = IDLE after fatal error"
                )
            }

        }, ERROR_DISPLAY_MS)
    }

    /**
     * Recoverable speech error.
     */
    private fun showRecoverableError(
        message: String
    ) {

        checkMainThread()

        cancelStartTimeout()

        fallbackInProgress = false

        isListening = false

        _state.value = MicState.ERROR

        Log.e(
            TAG,
            "RECOVERABLE ERROR: $message"
        )

        try {

            onErrorMessage(message)

        } catch (e: Exception) {

            Log.e(
                TAG,
                "onErrorMessage() threw exception",
                e
            )
        }

        mainHandler.postDelayed({

            if (_state.value == MicState.ERROR) {

                _state.value = MicState.IDLE

                Log.d(
                    TAG,
                    "STATE = IDLE after recoverable error"
                )
            }

        }, ERROR_DISPLAY_MS)
    }

    // -------------------------------------------------------------
    // ERROR MAPPING
    // -------------------------------------------------------------

    private fun mapErrorToMessage(
        error: Int
    ): String {

        return when (error) {

            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Speech network timeout हुआ।"

            SpeechRecognizer.ERROR_NETWORK ->
                "Internet connection check करें।"

            SpeechRecognizer.ERROR_AUDIO ->
                "Microphone audio शुरू नहीं हो सका।"

            SpeechRecognizer.ERROR_SERVER ->
                "Speech recognition server में समस्या आई।"

            SpeechRecognizer.ERROR_CLIENT ->
                "Speech recognizer client error आया।"

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "कुछ सुनाई नहीं दिया, दोबारा बोलिए।"

            SpeechRecognizer.ERROR_NO_MATCH ->
                "बात समझ में नहीं आई, दोबारा बोलिए।"

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "Speech recognizer busy है, कुछ सेकंड बाद फिर कोशिश करें।"

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Microphone permission नहीं मिली।"

            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS ->
                "बहुत ज्यादा speech requests भेजी गईं। थोड़ी देर बाद फिर कोशिश करें।"

            SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                /**
                 * IMPORTANT:
                 * Error 11 ONLY.
                 *
                 * Error 13 must NEVER come here.
                 */
                "Speech service से connection टूट गया।"

            ERROR_LANGUAGE_NOT_SUPPORTED_CODE ->
                "यह speech language supported नहीं है।"

            ERROR_LANGUAGE_UNAVAILABLE_CODE ->
                /**
                 * Normally handled by fallback before reaching here.
                 */
                "Speech language model अभी उपलब्ध नहीं है।"

            else ->
                "Speech recognition error आया। Code: $error"
        }
    }

    // -------------------------------------------------------------
    // ERROR NAME
    // -------------------------------------------------------------

    private fun errorName(
        error: Int
    ): String {

        return when (error) {

            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "ERROR_NETWORK_TIMEOUT"

            SpeechRecognizer.ERROR_NETWORK ->
                "ERROR_NETWORK"

            SpeechRecognizer.ERROR_AUDIO ->
                "ERROR_AUDIO"

            SpeechRecognizer.ERROR_SERVER ->
                "ERROR_SERVER"

            SpeechRecognizer.ERROR_CLIENT ->
                "ERROR_CLIENT"

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "ERROR_SPEECH_TIMEOUT"

            SpeechRecognizer.ERROR_NO_MATCH ->
                "ERROR_NO_MATCH"

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "ERROR_RECOGNIZER_BUSY"

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "ERROR_INSUFFICIENT_PERMISSIONS"

            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS ->
                "ERROR_TOO_MANY_REQUESTS"

            SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                "ERROR_SERVER_DISCONNECTED"

            ERROR_LANGUAGE_NOT_SUPPORTED_CODE ->
                "ERROR_LANGUAGE_NOT_SUPPORTED"

            ERROR_LANGUAGE_UNAVAILABLE_CODE ->
                "ERROR_LANGUAGE_UNAVAILABLE"

            else ->
                "UNKNOWN_ERROR"
        }
    }

    // -------------------------------------------------------------
    // MAIN THREAD SAFETY
    // -------------------------------------------------------------

    /**
     * Android SpeechRecognizer operations must happen on Main thread.
     */
    private fun checkMainThread() {

        check(
            Looper.myLooper() ==
                Looper.getMainLooper()
        ) {
            "NexusMicController SpeechRecognizer operation must run on Main thread"
        }
    }
}
