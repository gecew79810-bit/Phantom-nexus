package com.example.ai

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.ai.gemini.GeminiDebugEvent
import com.example.ai.gemini.GeminiDebugListener
import com.example.ai.gemini.GeminiErrorCode
import com.example.ai.gemini.GeminiFallbackState
import com.example.ai.gemini.GeminiTelemetryManager
import com.example.ai.nlp.SelfAttentionBrain
import com.example.bridge.AndroidSystemBridge
import com.example.bridge.CommandParserUtility
import com.example.hardware.HardwareActionResult
import com.example.hardware.HardwareController
import com.example.media.MaxMediaManager
import com.example.service.MaxAccessibilityService
import com.example.service.MaxNotificationBridge
import com.example.service.multimodal.MultimodalInputEngine
import com.example.telecom.MaxCallManager
import com.example.update.UpdateChecker
import com.example.voice.AssistantLanguage
import com.example.voice.EdgeTTSProvider
import com.example.voice.EdgeVoice
import com.example.voice.SpeechToTextProvider
import com.example.voice.TextToSpeechProvider
import com.example.voice.VoiceState
import com.example.voice.WakeWordDetector
import com.example.voice.WakeWordResult
import com.example.data.local.AppDatabase
import com.example.data.repository.EncryptedMemoryWalletRepository
import com.example.action.ActionPlanner
import com.example.action.MediaCommand
import com.example.action.MemoryOpType
import com.example.action.NexusAction
import com.example.action.NexusActionResult
import com.example.action.NexusActionRouter
import com.example.action.ScreenNavCommand
import com.example.action.VolumeDirection
import com.example.ai.briefing.SmartDailyBriefingEngine
import com.example.ai.dialogue.MultiTurnSessionManager
import com.example.ai.intelligence.PersonalizationEngine
import com.example.ai.intelligence.ProactiveAssistant
import com.example.context.NexusContextEngine
import com.example.security.NexusFeatureFlags
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import com.example.ai.orchestration.MultiAiOrchestrationManager
import com.pantham.nexus.interaction.NexusQueryRouter
import com.pantham.nexus.interaction.NexusRoute
import com.pantham.nexus.interaction.NexusRouteDecision
import com.pantham.nexus.interaction.NexusInteractionController
import com.pantham.nexus.voice.NexusVoiceController
import com.pantham.nexus.voice.NexusVoiceState
import java.util.Date
import java.util.Locale

enum class AiEngineMode(val displayName: String) {
    JARVIS_MULTI_AI("Jarvis Multi-AI Orchestration (Gemini + TokenRa)"),
    GEMINI_CLOUD("Google Gemini Cloud Fast"),
    OLLAMA_OFFLINE("100% Offline Ollama (Llama 3 8B)"),
    EMBEDDED_NEURAL("Offline Neural Brain (Zero Key & Private)")
}

class MaxOrchestrator(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    val systemBridge = AndroidSystemBridge(context)
    val commandParser = CommandParserUtility(context)
    val mediaManager = MaxMediaManager(context)
    var geminiService = GeminiService(context = context)
    val multiAiOrchestrator = MultiAiOrchestrationManager(context, geminiService)
    val multimodalEngine = MultimodalInputEngine(context, coroutineScope)
    val hardwareController = HardwareController(context, coroutineScope)

    val wakeWordDetector = WakeWordDetector(
        context = context,
        coroutineScope = coroutineScope,
        onWakeWordDetected = { wakeResult ->
            handleWakeWordTrigger(wakeResult)
        }
    )

    // Offline Intelligence, Neural Brain & Update Checker
    val updateChecker = UpdateChecker(context)
    val nlpBrain = SelfAttentionBrain(context)
    val ollamaService = OllamaLocalService(context, nlpBrain)

    // Room Database with SQLCipher Encryption Layer (Phantom Nexus Memory Wallet)
    private val encryptedDatabase = AppDatabase.getInstance(context)
    val memoryRepository = EncryptedMemoryWalletRepository(
        conversationDao = encryptedDatabase.conversationDao(),
        userPreferenceDao = encryptedDatabase.userPreferenceDao(),
        memoryWalletDao = encryptedDatabase.memoryWalletDao()
    )

    val queryRouter = NexusQueryRouter()

    val nexusVoiceController: NexusVoiceController by lazy {
        NexusVoiceController(
            context = context,
            onTranscript = { spokenText ->
                Log.d("NEXUS_VOICE", "[SPEECH_RESULT] Spoken text: '$spokenText'")
                _liveTranscript.value = spokenText
                processUserCommand(spokenText, isVoice = true)
            },
            onErrorMessage = { errorMsg ->
                Log.e("NEXUS_VOICE", "[SPEECH_ERROR] $errorMsg")
                _voiceState.value = VoiceState.IDLE
                addMessage("MAX", "⚠️ $errorMsg", isVoice = false)
            },
            onRmsChanged = { rms ->
                _rmsVolume.value = rms
            }
        )
    }

    val nexusInteractionController: NexusInteractionController by lazy {
        NexusInteractionController(
            processUserMessage = { query ->
                executeCanonicalUserQuery(query)
            },
            speakResponse = { speechText ->
                respondSpeechOnly(speechText)
            },
            onUserMessage = { userMsg ->
                addMessage("USER", userMsg, isVoice = false)
            },
            onAssistantMessage = { aiMsg ->
                addMessage("MAX", aiMsg, isVoice = false)
            },
            onError = { err ->
                addMessage("MAX", "⚠️ $err", isVoice = false)
            }
        )
    }

    private val _aiEngineMode = MutableStateFlow(AiEngineMode.GEMINI_CLOUD)
    val aiEngineMode: StateFlow<AiEngineMode> = _aiEngineMode.asStateFlow()

    private val _isPlayfulMode = MutableStateFlow(true)
    val isPlayfulMode: StateFlow<Boolean> = _isPlayfulMode.asStateFlow()

    private val _isEdgeTTSActive = MutableStateFlow(true)
    val isEdgeTTSActive: StateFlow<Boolean> = _isEdgeTTSActive.asStateFlow()

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _assistantState = MutableStateFlow(VoiceState.IDLE)
    val assistantState: StateFlow<VoiceState> = _assistantState.asStateFlow()

    fun setAssistantState(state: VoiceState) {
        _assistantState.value = state
        _voiceState.value = state
    }

    val contextEngine by lazy { NexusContextEngine(context, hardwareController, mediaManager) }
    val multiTurnManager by lazy { MultiTurnSessionManager(contextEngine) }
    val featureFlags by lazy { NexusFeatureFlags.getInstance(context) }
    val proactiveAssistant by lazy { ProactiveAssistant(context, featureFlags) }
    val personalizationEngine by lazy { PersonalizationEngine.getInstance(context) }
    val smartBriefingEngine by lazy { SmartDailyBriefingEngine(context, systemBridge, mediaManager, featureFlags) }
    val actionPlanner by lazy {
        ActionPlanner(
            systemBridge = systemBridge,
            hardwareController = hardwareController,
            mediaManager = mediaManager,
            ttsProvider = textToSpeech,
            onTaskUpdated = { updateTask(it) },
            coroutineScope = coroutineScope,
            context = context,
            actionRouter = actionRouter,
            contextEngine = contextEngine
        )
    }

    fun updateTask(updatedTask: TaskItem) {
        val current = _tasks.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == updatedTask.id }
        if (existingIndex != -1) {
            current[existingIndex] = updatedTask
        } else {
            current.add(0, updatedTask)
            if (current.size > 50) current.removeAt(current.size - 1)
        }
        _tasks.value = current
    }

    private val _activeCapability = MutableStateFlow(CapabilityNode.NONE)
    val activeCapability: StateFlow<CapabilityNode> = _activeCapability.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _rmsVolume = MutableStateFlow(0f)
    val rmsVolume: StateFlow<Float> = _rmsVolume.asStateFlow()

    private val _language = MutableStateFlow(AssistantLanguage.HINDI)
    val language: StateFlow<AssistantLanguage> = _language.asStateFlow()

    private val _isHandsFree = MutableStateFlow(false)
    val isHandsFree: StateFlow<Boolean> = _isHandsFree.asStateFlow()

    private val _pendingAction = MutableStateFlow<PendingAction?>(null)
    val pendingAction: StateFlow<PendingAction?> = _pendingAction.asStateFlow()

    private val _messages = MutableStateFlow<List<ConsoleMessage>>(
        listOf(
            ConsoleMessage(
                sender = "MAX",
                text = "नमस्ते बॉस! मैं MAX हूँ, आपका पर्सनल AI ऑपरेटिंग सिस्टम। बोलिए, मैं आपके फ़ोन में क्या करूँ?",
                timestamp = currentTime()
            )
        )
    )
    val messages: StateFlow<List<ConsoleMessage>> = _messages.asStateFlow()

    private val _tasks = MutableStateFlow<List<TaskItem>>(
        listOf(
            TaskItem(
                time = currentTime(),
                title = "All Core Hardware Systems Online",
                isCompleted = true,
                progress = 100,
                agent = AgentType.SYSTEM
            ),
            TaskItem(
                time = currentTime(),
                title = "Hindi & English Voice Engines Ready",
                isCompleted = true,
                progress = 100,
                agent = AgentType.COMMUNICATION
            )
        )
    )
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    // Gemini API Telemetry & Robust Debugging StateFlows
    val geminiRecentEvents: StateFlow<List<GeminiDebugEvent>> = GeminiTelemetryManager.recentEvents
    val geminiLatestEvent: StateFlow<GeminiDebugEvent?> = GeminiTelemetryManager.latestEvent
    val geminiLatestError: StateFlow<GeminiDebugEvent?> = GeminiTelemetryManager.latestError

    private val _agents = MutableStateFlow<Map<AgentType, AgentState>>(
        AgentType.entries.associateWith { AgentState(it, isActive = false) }
    )
    val agents: StateFlow<Map<AgentType, AgentState>> = _agents.asStateFlow()

    // Providers
    val speechToText = SpeechToTextProvider(
        context = context,
        onFinalText = { text ->
            _liveTranscript.value = text
            processUserCommand(text, isVoice = true)
        },
        onPartialText = { partial ->
            _liveTranscript.value = partial
        },
        onRmsChangedCallback = { rms ->
            _rmsVolume.value = rms
        },
        onListeningStateChanged = { isListening ->
            if (!isListening && _voiceState.value == VoiceState.LISTENING) {
                _voiceState.value = VoiceState.IDLE
                _activeCapability.value = CapabilityNode.NONE
                wakeWordDetector.resumeAfterCommand()
            }
        },
        onErrorCallback = { errorMsg ->
            if (_voiceState.value == VoiceState.LISTENING) {
                _voiceState.value = VoiceState.IDLE
                _activeCapability.value = CapabilityNode.NONE
                wakeWordDetector.resumeAfterCommand()
            }
            if (errorMsg.contains("permission", ignoreCase = true) || errorMsg.contains("not available", ignoreCase = true)) {
                addMessage("MAX", "⚠️ $errorMsg", isVoice = false)
            }
        }
    )

    val textToSpeech = TextToSpeechProvider(
        context = context,
        onSpeakingStateChanged = { speaking ->
            handleSpeakingState(speaking)
        }
    )

    val edgeTTS = EdgeTTSProvider(
        context = context,
        onSpeakingStateChanged = { speaking ->
            handleSpeakingState(speaking)
        }
    )

    init {
        // Observe and load encrypted conversation history from Room
        coroutineScope.launch(Dispatchers.IO) {
            memoryRepository.allMessages.collect { persistedMessages ->
                if (persistedMessages.isNotEmpty()) {
                    _messages.value = persistedMessages
                }
            }
        }

        // Restore persisted user preferences from Room
        coroutineScope.launch(Dispatchers.IO) {
            val savedEngine = memoryRepository.getPreference("ai_engine_mode")
            if (savedEngine != null) {
                try {
                    _aiEngineMode.value = AiEngineMode.valueOf(savedEngine)
                } catch (_: Exception) {}
            }

            val savedPlayful = memoryRepository.getPreference("is_playful_mode")
            if (savedPlayful != null) {
                val isPlayful = savedPlayful.toBoolean()
                _isPlayfulMode.value = isPlayful
                ollamaService.isPlayfulMode = isPlayful
            }

            val savedEdgeTTS = memoryRepository.getPreference("is_edge_tts_active")
            if (savedEdgeTTS != null) {
                _isEdgeTTSActive.value = savedEdgeTTS.toBoolean()
            }

            val savedVoice = memoryRepository.getPreference("edge_voice")
            if (savedVoice != null) {
                try {
                    edgeTTS.currentVoice = EdgeVoice.valueOf(savedVoice)
                } catch (_: Exception) {}
            }

            val savedLang = memoryRepository.getPreference("assistant_language")
            if (savedLang != null) {
                try {
                    _language.value = AssistantLanguage.valueOf(savedLang)
                } catch (_: Exception) {}
            }

            val savedHandsFree = memoryRepository.getPreference("hands_free_mode")
            if (savedHandsFree != null) {
                _isHandsFree.value = savedHandsFree.toBoolean()
            }
        }

        // Initialize wake word detection only if hands-free mode is explicitly enabled
        if (_isHandsFree.value && wakeWordDetector.hasPermission()) {
            wakeWordDetector.startWakeWordListening()
        }

        // Register robust Gemini API Telemetry & Debugging Listener
        GeminiTelemetryManager.addListener(object : GeminiDebugListener {
            override fun onApiCallInitiated(
                requestId: String,
                model: String,
                promptSnippet: String,
                isMultimodal: Boolean
            ) {
                Log.d("MaxOrchestrator", "[GeminiTelemetry] Dispatched request $requestId to $model (multimodal=$isMultimodal): $promptSnippet")
            }

            override fun onApiResponseSuccess(event: GeminiDebugEvent) {
                Log.d("MaxOrchestrator", "[GeminiTelemetry] Success: ${event.toFormattedLog()}")
            }

            override fun onApiErrorCaught(event: GeminiDebugEvent) {
                Log.w("MaxOrchestrator", "[GeminiTelemetry] Error Caught: ${event.toFormattedLog()}")
                addTask("Gemini Error: [${event.errorCode.httpCode} ${event.errorCode.shortTitle}] in ${event.model}", AgentType.SYSTEM)
            }

            override fun onFallbackActivated(event: GeminiDebugEvent, fallbackReason: String) {
                Log.i("MaxOrchestrator", "[GeminiTelemetry] Fallback Activated: $fallbackReason for ${event.errorCode.shortTitle}")
            }
        })
    }

    private fun handleSpeakingState(speaking: Boolean) {
        if (speaking) {
            _voiceState.value = VoiceState.SPEAKING
            wakeWordDetector.pauseForCommand()
        } else {
            if (_voiceState.value == VoiceState.SPEAKING) {
                _voiceState.value = VoiceState.IDLE
                if (_isHandsFree.value) {
                    coroutineScope.launch {
                        delay(500)
                        startListening()
                    }
                } else {
                    wakeWordDetector.resumeAfterCommand()
                }
            }
        }
    }

    /**
     * Handles instant detection of wake words ("wake panthom", "wake pantham", "wake phantom").
     * "ek dum se sunn le" - immediate response and seamless voice capture.
     */
    fun handleWakeWordTrigger(wakeResult: WakeWordResult) {
        val trailing = wakeResult.trailingCommand.trim()
        Log.i("MaxOrchestrator", "Wake word detected: '${wakeResult.matchedWakePhrase}', command: '$trailing'")

        _activeCapability.value = CapabilityNode.THINK
        _voiceState.value = VoiceState.LISTENING

        if (trailing.isNotBlank()) {
            addMessage("USER", "${wakeResult.matchedWakePhrase} $trailing", isVoice = true)
            processUserCommand(trailing, isVoice = true)
        } else {
            addMessage("USER", wakeResult.matchedWakePhrase, isVoice = true)
            val wakeAcks = listOf(
                "हाँ बॉस, बोलिए! मैं सुन रहा हूँ।",
                "Yes Boss! Listening...",
                "आज्ञा दीजिए बॉस, मैं तैयार हूँ।"
            )
            val ack = wakeAcks.random()
            respond(ack)
            coroutineScope.launch {
                delay(700)
                startListening()
            }
        }
    }

    val callManager: MaxCallManager by lazy {
        MaxCallManager(
            context = context,
            coroutineScope = coroutineScope,
            ttsProvider = textToSpeech,
            onIncomingCallDetected = { name, num ->
                addMessage("SYSTEM", "📞 Incoming Call from $name ($num)")
                addTask("Screening call: $name", AgentType.COMMUNICATION)
                if (!_isHandsFree.value) {
                    startListening()
                }
            }
        )
    }

    val actionRouter: NexusActionRouter by lazy {
        NexusActionRouter(
            context = context,
            systemBridge = systemBridge,
            hardwareController = hardwareController,
            mediaManager = mediaManager,
            memoryRepository = memoryRepository,
            callManager = callManager,
            ttsProvider = textToSpeech,
            onTaskUpdated = { updatedTask ->
                val current = _tasks.value.toMutableList()
                val existingIndex = current.indexOfFirst { it.id == updatedTask.id }
                if (existingIndex != -1) {
                    current[existingIndex] = updatedTask
                } else {
                    current.add(0, updatedTask)
                    if (current.size > 50) current.removeAt(current.size - 1)
                }
                _tasks.value = current
            },
            onRequestConfirmation = { pending ->
                _pendingAction.value = pending
            }
        )
    }

    fun setAiEngineMode(mode: AiEngineMode) {
        _aiEngineMode.value = mode
        coroutineScope.launch(Dispatchers.IO) {
            memoryRepository.savePreference("ai_engine_mode", mode.name, "AI_ENGINE")
        }
    }

    fun setPlayfulMode(enabled: Boolean) {
        _isPlayfulMode.value = enabled
        ollamaService.isPlayfulMode = enabled
        coroutineScope.launch(Dispatchers.IO) {
            memoryRepository.savePreference("is_playful_mode", enabled.toString(), "AI_ENGINE")
        }
    }

    fun setEdgeTTSActive(enabled: Boolean) {
        _isEdgeTTSActive.value = enabled
        coroutineScope.launch(Dispatchers.IO) {
            memoryRepository.savePreference("is_edge_tts_active", enabled.toString(), "VOICE")
        }
    }

    fun setEdgeVoice(voice: EdgeVoice) {
        edgeTTS.currentVoice = voice
        coroutineScope.launch(Dispatchers.IO) {
            memoryRepository.savePreference("edge_voice", voice.name, "VOICE")
        }
    }

    fun setLanguage(lang: AssistantLanguage) {
        _language.value = lang
        coroutineScope.launch(Dispatchers.IO) {
            memoryRepository.savePreference("assistant_language", lang.name, "GENERAL")
        }
    }

    fun toggleHandsFree() {
        val newState = !_isHandsFree.value
        _isHandsFree.value = newState
        if (newState) {
            if (wakeWordDetector.hasPermission()) {
                wakeWordDetector.startWakeWordListening()
            }
        } else {
            wakeWordDetector.stopWakeWordListening()
        }
        coroutineScope.launch(Dispatchers.IO) {
            memoryRepository.savePreference("hands_free_mode", newState.toString(), "GENERAL")
        }
    }

    fun updateCustomApiKey(key: String) {
        try {
            val prefs = context.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("gemini_api_key", key).apply()
        } catch (_: Exception) {}
        geminiService = GeminiService(key, context)
    }

    fun startListening() {
        textToSpeech.stop()
        edgeTTS.stop()
        wakeWordDetector.pauseForCommand()
        _voiceState.value = VoiceState.LISTENING
        _activeCapability.value = CapabilityNode.THINK
        speechToText.startListening(_language.value)
    }

    fun stopListening() {
        speechToText.stopListening()
        _voiceState.value = VoiceState.IDLE
        _activeCapability.value = CapabilityNode.NONE
        wakeWordDetector.resumeAfterCommand()
    }

    fun clearPendingAction() {
        _pendingAction.value = null
    }

    fun emergencyStop() {
        speechToText.stopListening()
        textToSpeech.stop()
        edgeTTS.stop()
        wakeWordDetector.resumeAfterCommand()
        _pendingAction.value = null
        _voiceState.value = VoiceState.IDLE
        _activeCapability.value = CapabilityNode.NONE
        setAgentActive(AgentType.SYSTEM, false)
        addMessage("MAX", "इमरजेंसी स्टॉप सक्रिय: सभी कार्य तुरंत रोक दिए गए हैं।")
    }

    private fun currentTime(): String =
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

    fun stopTts() {
        textToSpeech.stop()
        edgeTTS.stop()
        if (_voiceState.value == VoiceState.SPEAKING) {
            _voiceState.value = VoiceState.IDLE
            _activeCapability.value = CapabilityNode.NONE
        }
    }

    fun addErrorMessage(message: String) {
        addMessage("MAX", "⚠️ $message", isVoice = false)
    }

    fun addMessage(sender: String, text: String, isVoice: Boolean = false) {
        val msg = ConsoleMessage(
            sender = sender,
            text = text,
            timestamp = currentTime(),
            isVoice = isVoice
        )
        _messages.value = _messages.value + msg

        // Persist to encrypted SQLCipher Room database
        coroutineScope.launch(Dispatchers.IO) {
            memoryRepository.saveMessage(msg)
        }
    }

    fun deleteMessage(id: String) {
        _messages.value = _messages.value.filter { it.id != id }
    }

    fun clearConversationHistory() {
        _messages.value = listOf(
            ConsoleMessage(
                sender = "MAX",
                text = "कन्वर्सेशन हिस्ट्री सुरक्षित रूप से साफ़ कर दी गई है।",
                timestamp = currentTime()
            )
        )
        coroutineScope.launch(Dispatchers.IO) {
            memoryRepository.clearHistory()
        }
    }

    fun addTask(
        title: String,
        agent: AgentType = AgentType.SYSTEM,
        status: ActionStatus = ActionStatus.SUCCESS,
        id: String = "ACT_" + System.currentTimeMillis() + "_" + (1000..9999).random(),
        details: String = ""
    ) {
        val task = TaskItem(
            id = id,
            time = currentTime(),
            title = title,
            isCompleted = status == ActionStatus.SUCCESS || status == ActionStatus.FAILED,
            progress = if (status == ActionStatus.SUCCESS) 100 else 50,
            agent = agent,
            status = status,
            details = details
        )
        _tasks.value = listOf(task) + _tasks.value
    }

    private fun setAgentActive(agent: AgentType, active: Boolean, action: String = "Active") {
        val current = _agents.value.toMutableMap()
        current[agent] = AgentState(agent, isActive = active, lastAction = action)
        _agents.value = current
    }

    fun processUserCommand(rawCommand: String, isVoice: Boolean = false) {
        val command = rawCommand.trim()
        if (command.isEmpty()) return

        // Check if command is or starts with the wake word
        val wakeMatch = wakeWordDetector.extractWakeWord(command)
        val cleanCommand = if (wakeMatch != null) {
            if (wakeMatch.trailingCommand.isNotBlank()) {
                wakeMatch.trailingCommand
            } else {
                // Just wake word uttered
                addMessage("USER", command, isVoice)
                _voiceState.value = VoiceState.IDLE
                val wakeAcks = listOf(
                    "हाँ बॉस! बताइए, मैं सुन रहा हूँ।",
                    "Yes Boss! Listening...",
                    "जी बॉस, आज्ञा दीजिए।"
                )
                val reply = wakeAcks.random()
                respond(reply, speakAloud = true)
                coroutineScope.launch {
                    delay(700)
                    startListening()
                }
                return
            }
        } else {
            command
        }

        addMessage("USER", command, isVoice)
        _voiceState.value = VoiceState.THINKING
        _activeCapability.value = CapabilityNode.THINK

        coroutineScope.launch {
            val replyVoice = isVoice
            fun respond(text: String) {
                this@MaxOrchestrator.respond(text, speakAloud = replyVoice)
            }
            val lower = cleanCommand.lowercase()
            val routeDecision = queryRouter.route(cleanCommand)
            Log.d("NEXUS_ROUTER", "[DECISION] Command: '$cleanCommand' -> Route: ${routeDecision.route}")
            val currentCall = callManager.callState.value

            contextEngine.recordCommand(cleanCommand)
            personalizationEngine.setPreferredLanguage(_language.value)

            // Multi-Turn conversation check (follow-up slots & confirmations)
            val followUpResult = multiTurnManager.processFollowUp(cleanCommand, _language.value)
            if (followUpResult.handled) {
                if (followUpResult.readyAction != null) {
                    setAssistantState(VoiceState.EXECUTING)
                    val res = actionRouter.routeAction(followUpResult.readyAction, bypassConfirmation = true, language = _language.value)
                    setAssistantState(if (res.success) VoiceState.SUCCESS else VoiceState.FAILED)
                }
                respond(followUpResult.responseSpeech)
                return@launch
            }

            val incompleteCheck = multiTurnManager.checkIncompleteCommand(cleanCommand, _language.value)
            if (incompleteCheck != null && incompleteCheck.handled) {
                respond(incompleteCheck.responseSpeech)
                return@launch
            }

            // Autonomous Task Control (Pause / Resume / Cancel)
            if (lower == "stop" || lower == "ruko" || lower == "रुको" || lower == "pause" || lower == "wait") {
                val pauseMsg = actionPlanner.pauseActiveGoal()
                respond(pauseMsg)
                return@launch
            }
            if (lower == "continue" || lower == "resume" || lower == "aage badho" || lower == "आगे बढ़ो" || lower == "shuru karo") {
                val resumed = actionPlanner.resumeActiveGoal(_language.value) { stepRes ->
                    respond(stepRes.message)
                }
                if (resumed) {
                    respond(if (_language.value == AssistantLanguage.HINDI) "मैंने रुका हुआ काम फिर से शुरू कर दिया है।" else "Resuming your paused task from safe checkpoint.")
                    return@launch
                }
            }
            if (lower.contains("cancel this") || lower.contains("stop everything") || lower.contains("cancel task") || lower.contains("stop the current task")) {
                val cancelMsg = actionPlanner.cancelActiveGoal()
                respond(cancelMsg)
                return@launch
            }

            // Autonomous Goal & Compound Command Handling (DAG execution)
            if (actionPlanner.isGoalOrCompoundCommand(cleanCommand)) {
                setAssistantState(VoiceState.EXECUTING)
                setAgentActive(AgentType.PLANNER, true, "Executing Autonomous Goal DAG")
                actionPlanner.executeAutonomousGoal(cleanCommand, _language.value) { goalRes ->
                    setAssistantState(if (goalRes.success) VoiceState.SUCCESS else VoiceState.FAILED)
                    setAgentActive(AgentType.PLANNER, false)
                    respond(goalRes.message)
                }
                return@launch
            }

            // Proactive Assistant check
            proactiveAssistant.checkProactiveConditions()

            // Smart Daily Briefing
            if (lower.contains("briefing") || lower.contains("daily update") || lower.contains("aaj ka update") || lower.contains("kya update hai")) {
                setAssistantState(VoiceState.THINKING)
                setAgentActive(AgentType.RESEARCH, true, "Synthesizing Daily Briefing")
                val briefing = smartBriefingEngine.generateBriefing(_language.value)
                respond(briefing)
                setAssistantState(VoiceState.SUCCESS)
                setAgentActive(AgentType.RESEARCH, false)
                return@launch
            }

            // Memory inspection (Requirement 16: What do you remember about me?)
            if (lower.contains("remember about me") || lower.contains("kya yaad hai") || lower.contains("mere baare mein kya") || lower.contains("what do you know about me")) {
                setAssistantState(VoiceState.THINKING)
                setAgentActive(AgentType.MEMORY, true, "Accessing Vault")
                val prefs = memoryRepository.allPreferences.firstOrNull() ?: emptyList()
                val items = memoryRepository.walletItems.firstOrNull() ?: emptyList()
                val summary = if (prefs.isEmpty() && items.isEmpty()) {
                    if (_language.value == AssistantLanguage.HINDI) "मुझे अभी आपके बारे में कोई सुरक्षित तथ्य याद नहीं है।" else "I do not have any stored facts about you in the memory wallet."
                } else {
                    val facts = (prefs.map { "${it.key}: ${it.value}" } + items.map { "${it.tag}: ${it.title}" }).take(5).joinToString(", ")
                    if (_language.value == AssistantLanguage.HINDI) "मुझे आपकी ये बातें याद हैं: $facts" else "I have saved: $facts"
                }
                respond(summary)
                setAssistantState(VoiceState.SUCCESS)
                setAgentActive(AgentType.MEMORY, false)
                return@launch
            }

            // 0A. Incoming Call Voice Screening: Hands-free Answer / Reject
            if (currentCall.isRinging) {
                if (lower.contains("answer") || lower.contains("accept") || lower.contains("uthao") || lower.contains("उठाओ") || lower.contains("haan") || lower.contains("pick up") || lower.contains("कॉल उठाओ")) {
                    setAgentActive(AgentType.COMMUNICATION, true, "Answering Call")
                    val (success, msg) = callManager.answerCall()
                    respond(if (success) "कॉल उठा ली गई है बॉस।" else msg)
                    addTask("Call Answered: ${currentCall.callerName}", AgentType.COMMUNICATION)
                    setAgentActive(AgentType.COMMUNICATION, false)
                    return@launch
                }
                if (lower.contains("reject") || lower.contains("decline") || lower.contains("kaat") || lower.contains("काट") || lower.contains("cut") || lower.contains("कॉल काटो") || lower.contains("cancel")) {
                    setAgentActive(AgentType.COMMUNICATION, true, "Rejecting Call")
                    val (success, msg) = callManager.rejectCall()
                    respond(if (success) "कॉल काट दी गई है बॉस।" else msg)
                    addTask("Call Rejected: ${currentCall.callerName}", AgentType.COMMUNICATION)
                    setAgentActive(AgentType.COMMUNICATION, false)
                    return@launch
                }
            }

            // 0B. Call Memo / Voice Note Recording (100% BYOK Local Encrypted Storage)
            if (lower.contains("record call") || lower.contains("call note") || lower.contains("कॉल नोट") || (lower.contains("रिकॉर्ड") && (lower.contains("कॉल") || lower.contains("नोट")))) {
                val (success, msg) = callManager.startRecordingMemo()
                respond(if (success) "कॉल नोट की रिकॉर्डिंग शुरू हो गई है बॉस। यह केवल आपके डिवाइस में सुरक्षित है।" else msg)
                addTask("Call Recording Started", AgentType.COMMUNICATION)
                return@launch
            }
            if (lower.contains("stop record") || lower.contains("रिकॉर्डिंग रोको") || lower.contains("रिकॉर्ड बंद")) {
                val (success, msg) = callManager.stopRecordingMemo()
                respond(msg)
                addTask("Call Recording Finished", AgentType.COMMUNICATION)
                return@launch
            }

            // 0C. System-Wide Media Controller (Spotify, YouTube, Apple Music, Podcasts)
            if (lower.contains("play music") || lower.contains("गाना बजाओ") || lower.contains("गाना चलाओ") || lower.contains("म्यूजिक बजाओ") || lower.contains("spotify play") || lower == "play" || lower == "प्ले") {
                setAgentActive(AgentType.SYSTEM, true, "Playing Media")
                val (_, res) = mediaManager.play()
                respond("म्यूजिक प्ले कर दिया गया है बॉस।")
                addTask("Media Playback: $res", AgentType.SYSTEM)
                setAgentActive(AgentType.SYSTEM, false)
                return@launch
            }
            if (lower.contains("pause music") || lower.contains("गाना रोको") || lower.contains("गाना बंद") || lower.contains("म्यूजिक रोको") || lower == "pause" || lower == "पॉज़" || lower.contains("stop music")) {
                setAgentActive(AgentType.SYSTEM, true, "Pausing Media")
                val (_, res) = mediaManager.pause()
                respond("म्यूजिक पॉज़ कर दिया गया है बॉस।")
                addTask("Media Paused: $res", AgentType.SYSTEM)
                setAgentActive(AgentType.SYSTEM, false)
                return@launch
            }
            if (lower.contains("next song") || lower.contains("अगला गाना") || lower.contains("next track") || lower.contains("गाना बदलो") || lower.contains("skip track")) {
                setAgentActive(AgentType.SYSTEM, true, "Next Track")
                val (_, res) = mediaManager.skipToNext()
                respond("अगला गाना चला दिया है बॉस।")
                addTask("Media Next: $res", AgentType.SYSTEM)
                setAgentActive(AgentType.SYSTEM, false)
                return@launch
            }
            if (lower.contains("previous song") || lower.contains("पिछला गाना") || lower.contains("prev track") || lower.contains("pichla gana")) {
                setAgentActive(AgentType.SYSTEM, true, "Previous Track")
                val (_, res) = mediaManager.skipToPrevious()
                respond("पिछला गाना प्ले किया जा रहा है।")
                addTask("Media Previous: $res", AgentType.SYSTEM)
                setAgentActive(AgentType.SYSTEM, false)
                return@launch
            }

            // 0D. Autonomous Auto-Reply Controls
            if (lower.contains("auto reply on") || lower.contains("ऑटो रिप्लाई चालू") || lower.contains("enable auto reply")) {
                if (!MaxNotificationBridge.isAutoReplyEnabled.value) {
                    MaxNotificationBridge.toggleAutoReply()
                }
                respond("ऑटोनॉमस ऑटो-रिप्लाई सक्रिय कर दिया गया है बॉस।")
                addTask("Auto-Reply Activated", AgentType.COMMUNICATION)
                return@launch
            }
            if (lower.contains("auto reply off") || lower.contains("ऑटो रिप्लाई बंद") || lower.contains("disable auto reply")) {
                if (MaxNotificationBridge.isAutoReplyEnabled.value) {
                    MaxNotificationBridge.toggleAutoReply()
                }
                respond("ऑटोनॉमस ऑटो-रिप्लाई बंद कर दिया गया है।")
                addTask("Auto-Reply Deactivated", AgentType.COMMUNICATION)
                return@launch
            }

            // 0E. Morning Routine & Macro Automations (Parallel & Sequential Action Planning)
            if (lower.contains("morning routine") || lower.contains("मॉर्निंग रूटीन") || lower.contains("start routine") || lower.contains("डेली रूटीन") || lower.contains("daily routine")) {
                setAssistantState(VoiceState.PLANNING)
                setAgentActive(AgentType.PLANNER, true, "Executing Action Pipeline")
                actionPlanner.executeMorningRoutine(_language.value) { briefingSpeech ->
                    respond(briefingSpeech)
                    setAssistantState(VoiceState.SUCCESS)
                }
                setAgentActive(AgentType.PLANNER, false)
                return@launch
            }

            // 0F. Persistent Encrypted Memory & Wallet
            val isRememberCommand = (
                lower.startsWith("remember that") || lower.startsWith("remember ") ||
                lower.contains("याद रखो कि") || lower.contains("याद रखना कि") || lower.contains("याद रखो")
            )
            if (isRememberCommand) {
                setAgentActive(AgentType.MEMORY, true, "Encoding Memory")
                _activeCapability.value = CapabilityNode.LEARN
                var clean = lower
                listOf("remember that", "remember", "याद रखो कि", "याद रखना कि", "याद रखो", "please").forEach {
                    clean = clean.replace(it, "")
                }
                clean = clean.trim()
                val (key, value) = if (clean.contains(" is ")) {
                    val parts = clean.split(" is ", limit = 2)
                    Pair(parts[0].replace("my ", "").trim(), parts[1].trim())
                } else if (clean.contains(" है")) {
                    val parts = clean.replace(" है", "").split(" ", limit = 2)
                    Pair(parts.firstOrNull()?.replace("मेरा ", "")?.replace("मेरी ", "")?.trim() ?: "fact", clean.trim())
                } else {
                    Pair("note", clean)
                }

                val action = NexusAction.Memory(MemoryOpType.REMEMBER, key, value)
                val res = actionRouter.routeAction(action, bypassConfirmation = true, language = _language.value)
                respond(res.speechFeedback ?: res.message)
                setAgentActive(AgentType.MEMORY, false)
                return@launch
            }

            val isRecallCommand = (
                lower.startsWith("what is my") || lower.startsWith("what's my") ||
                lower.startsWith("tell me my") || lower.contains("मेरा पसंदीदा") ||
                lower.contains("मेरी पसंदीदा") || (lower.contains("क्या है") && (lower.contains("मेरा") || lower.contains("मेरी")))
            )
            if (isRecallCommand) {
                setAgentActive(AgentType.MEMORY, true, "Recalling Memory")
                _activeCapability.value = CapabilityNode.ANALYZE
                var queryKey = lower
                listOf("what is my", "what's my", "tell me my", "what is the", "do you remember my", "मेरा", "मेरी", "क्या है", "?").forEach {
                    queryKey = queryKey.replace(it, "")
                }
                queryKey = queryKey.trim()
                val action = NexusAction.Memory(MemoryOpType.RECALL, queryKey)
                val res = actionRouter.routeAction(action, bypassConfirmation = true, language = _language.value)
                respond(res.speechFeedback ?: res.message)
                setAgentActive(AgentType.MEMORY, false)
                return@launch
            }

            if (lower.contains("clear memory") || lower.contains("wipe memory") || lower.contains("मेमोरी मिटाओ") || lower.contains("मेमोरी खाली करो")) {
                val action = NexusAction.Memory(MemoryOpType.CLEAR_ALL, "all")
                actionRouter.routeAction(action, bypassConfirmation = false, language = _language.value)
                return@launch
            }

            // 0G. Calendar & Schedules
            if (lower == "calendar" || lower == "कैलेंडर" || lower.contains("open calendar") || lower.contains("कैलेंडर खोलो") || lower.contains("schedule today") || lower.contains("schedule reminders")) {
                setAgentActive(AgentType.SYSTEM, true, "Opening Calendar")
                val action = NexusAction.OpenCalendar()
                val res = actionRouter.routeAction(action, bypassConfirmation = true, language = _language.value)
                respond(if (res.success) "कैलेंडर खोल दिया गया है बॉस।" else res.message)
                setAgentActive(AgentType.SYSTEM, false)
                return@launch
            }

            // 0H. Read Recent Notifications
            if (lower.contains("read notifications") || lower.contains("read notification") || lower.contains("नोटिफिकेशन पढ़ो") || lower.contains("नोटिफिकेशन सुनाओ")) {
                setAgentActive(AgentType.COMMUNICATION, true, "Reading Notifications")
                val action = NexusAction.ReadNotifications()
                val res = actionRouter.routeAction(action, bypassConfirmation = true, language = _language.value)
                respond(res.message)
                setAgentActive(AgentType.COMMUNICATION, false)
                return@launch
            }

            // 1. WhatsApp Commands
            if (lower.contains("whatsapp") || lower.contains("व्हाट्सएप") || lower.contains("वाट्सएप")) {
                setAgentActive(AgentType.COMMUNICATION, true, "Launching WhatsApp")
                _activeCapability.value = CapabilityNode.EXECUTE

                if (lower.contains("bhejo") || lower.contains("send") || lower.contains("भेजो") || lower.contains("message")) {
                    // Extraction: extract phone / message
                    val cleanText = extractMessageText(command)
                    val phone = extractPhoneNumber(command)
                    _pendingAction.value = PendingAction(
                        title = "WhatsApp संदेश भेजें",
                        description = "क्या आप WhatsApp पर संदेश भेजना चाहते हैं?\nसंदेश: \"${cleanText.ifEmpty { "नमस्ते" }}\"${if (phone != null) "\nनंबर: $phone" else ""}",
                        riskLevel = RiskLevel.MEDIUM,
                        onConfirm = {
                            _pendingAction.value = null
                            val (success, msg) = systemBridge.openWhatsAppChatOrShare(phone, cleanText.ifEmpty { null })
                            respond(if (success) "WhatsApp पर मैसेज भेज दिया बॉस।" else "WhatsApp खोलने में त्रुटि: $msg")
                            addTask("WhatsApp Share: $cleanText", AgentType.COMMUNICATION)
                        },
                        onCancel = {
                            _pendingAction.value = null
                            respond("कार्रवाई रद्द कर दी गई।")
                        }
                    )
                    respond("बॉस, WhatsApp मैसेज तैयार है। पुष्टि करें।")
                } else {
                    val (success, msg) = systemBridge.openWhatsAppChatOrShare()
                    respond(if (success) "WhatsApp खोल दिया है बॉस।" else "WhatsApp खोलने में समस्या: $msg")
                    addTask("Open WhatsApp", AgentType.COMMUNICATION)
                }
                setAgentActive(AgentType.COMMUNICATION, false)
                return@launch
            }

            // 2. Phone Call Commands
            if (lower.contains("call") || lower.contains("कॉल") || lower.contains("फोन मिलाओ")) {
                setAgentActive(AgentType.COMMUNICATION, true, "Dialing")
                _activeCapability.value = CapabilityNode.EXECUTE
                val number = extractPhoneNumber(command) ?: "100"
                _pendingAction.value = PendingAction(
                    title = "फ़ोन कॉल करें",
                    description = "क्या आप $number पर कॉल मिलाना चाहते हैं?",
                    riskLevel = RiskLevel.HIGH,
                    onConfirm = {
                        _pendingAction.value = null
                        val (success, msg) = systemBridge.initiateCall(number, requireDirectCall = true)
                        respond(if (success) "$number पर कॉल मिलाई जा रही है बॉस।" else msg)
                        addTask("Place Call to $number", AgentType.COMMUNICATION)
                    },
                    onCancel = {
                        _pendingAction.value = null
                        respond("कॉल रद्द कर दी गई।")
                    }
                )
                respond("बॉस, क्या मैं $number पर कॉल करूँ?")
                setAgentActive(AgentType.COMMUNICATION, false)
                return@launch
            }

            // 3. SMS Commands
            if (lower.contains("sms") || lower.contains("मैसेज") || lower.contains("संदेश")) {
                setAgentActive(AgentType.COMMUNICATION, true, "Drafting SMS")
                _activeCapability.value = CapabilityNode.EXECUTE
                val phone = extractPhoneNumber(command) ?: ""
                val text = extractMessageText(command).ifEmpty { "नमस्ते" }
                _pendingAction.value = PendingAction(
                    title = "SMS भेजें",
                    description = "क्या आप यह SMS भेजना चाहते हैं?\nनंबर: ${phone.ifEmpty { "चुना गया नंबर" }}\nसंदेश: \"$text\"",
                    riskLevel = RiskLevel.HIGH,
                    onConfirm = {
                        _pendingAction.value = null
                        val (success, msg) = if (phone.isNotEmpty()) {
                            systemBridge.sendSmsDirect(phone, text)
                        } else {
                            systemBridge.openSmsCompose(null, text)
                        }
                        respond(if (success) "SMS भेज दिया गया बॉस।" else msg)
                        addTask("Send SMS to $phone", AgentType.COMMUNICATION)
                    },
                    onCancel = {
                        _pendingAction.value = null
                        respond("SMS रद्द कर दिया गया।")
                    }
                )
                respond("बॉस, SMS तैयार है। क्या मैं भेज दूँ?")
                setAgentActive(AgentType.COMMUNICATION, false)
                return@launch
            }

            // 3.4 Dedicated Command Parser Utility (Android Settings Intents & Image Gen Deep Links)
            val parsedUtilityCommand = commandParser.parse(cleanCommand)
            if (parsedUtilityCommand is CommandParserUtility.ParsedCommand.ImageGenerationCommand) {
                setAgentActive(AgentType.RESEARCH, true, "Synthesizing AI Image & Deep Link Dispatch")
                _activeCapability.value = CapabilityNode.AUTOMATE

                val execResult = commandParser.execute(parsedUtilityCommand, systemBridge)
                val replyText = """
                    बॉस, इमेज जनरेशन और ऑटोमेशन सफल:
                    1. प्रॉम्प्ट लिखकर ${parsedUtilityCommand.platform.displayName} खोल दिया गया है: "${parsedUtilityCommand.masterArtPrompt}"
                    2. आपकी इमेज "${parsedUtilityCommand.prompt}" बनाकर सीधे फ़ोन गैलरी (Pictures/Phantom AI) में सफलतापूर्वक सेव कर दी गई है!
                """.trimIndent()

                respond(replyText)
                addTask("AI Image Generated & Deep Link (${parsedUtilityCommand.prompt})", AgentType.RESEARCH)
                setAgentActive(AgentType.RESEARCH, false)
                return@launch
            } else if (parsedUtilityCommand is CommandParserUtility.ParsedCommand.SettingsIntentCommand) {
                setAgentActive(AgentType.SYSTEM, true, "Executing Settings Intent (${parsedUtilityCommand.titleEnglish})")
                _activeCapability.value = CapabilityNode.EXECUTE

                val execResult = commandParser.execute(parsedUtilityCommand, systemBridge)
                // Direct status feedback to the user without synthetic voice output
                this@MaxOrchestrator.respond(execResult.directStatusFeedback, speakAloud = !execResult.suppressVoiceOutput)
                addTask("Settings Intent: ${parsedUtilityCommand.titleEnglish}", AgentType.SYSTEM)
                setAgentActive(AgentType.SYSTEM, false)
                return@launch
            }

            // 3.5 Direct Hardware Subsystem (Wi-Fi, Bluetooth, Flashlight, Volume)
            if (hardwareController.handleVoiceCommand(cleanCommand) { actionResult ->
                when (actionResult) {
                    is HardwareActionResult.Success -> {
                        respond(actionResult.speechFeedback)
                        addTask(actionResult.message, AgentType.SYSTEM)
                    }
                    is HardwareActionResult.DirectIntent -> {
                        respond(actionResult.speechFeedback)
                        addTask(actionResult.message, AgentType.SYSTEM)
                    }
                    is HardwareActionResult.PermissionRequired -> {
                        respond(actionResult.speechFeedback)
                        addTask(actionResult.message, AgentType.SYSTEM)
                    }
                    is HardwareActionResult.Error -> {
                        respond(actionResult.speechFeedback)
                        addTask(actionResult.error, AgentType.SYSTEM)
                    }
                }
            }) {
                setAgentActive(AgentType.SYSTEM, false)
                return@launch
            }

            // 4. Torch / Flashlight Commands
            if (lower.contains("torch") || lower.contains("flashlight") || lower.contains("टॉर्च") || lower.contains("लाइट")) {
                setAgentActive(AgentType.SYSTEM, true, "Toggling Flashlight")
                _activeCapability.value = CapabilityNode.EXECUTE
                val turnOn = !lower.contains("band") && !lower.contains("off") && !lower.contains("बंद")
                val (success, status) = systemBridge.toggleTorch(turnOn)
                respond(if (success) (if (turnOn) "टॉर्च चालू कर दी है बॉस।" else "टॉर्च बंद कर दी है।") else status)
                addTask("Flashlight: $status", AgentType.SYSTEM)
                setAgentActive(AgentType.SYSTEM, false)
                return@launch
            }

            // 5. Volume Controls
            if (lower.contains("volume") || lower.contains("वॉल्यूम") || lower.contains("आवाज")) {
                setAgentActive(AgentType.SYSTEM, true, "Volume Control")
                _activeCapability.value = CapabilityNode.EXECUTE
                val res = when {
                    lower.contains("badhao") || lower.contains("up") || lower.contains("बढ़ाओ") || lower.contains("बढ़ाओ") ->
                        systemBridge.adjustVolume(AudioManager.ADJUST_RAISE)
                    lower.contains("kam") || lower.contains("down") || lower.contains("कम") ->
                        systemBridge.adjustVolume(AudioManager.ADJUST_LOWER)
                    else -> systemBridge.setVolumePercent(70)
                }
                respond("बॉस, $res")
                addTask("Adjust Volume: $res", AgentType.SYSTEM)
                setAgentActive(AgentType.SYSTEM, false)
                return@launch
            }

            // 6A. Real System Diagnostics & Specs - RESTRICTED ONLY TO EXPLICIT DEVICE_STATUS ROUTE
            if (routeDecision.route == NexusRoute.DEVICE_STATUS) {
                setAgentActive(AgentType.SYSTEM, true, "Device Status & Diagnostics")
                _activeCapability.value = CapabilityNode.ANALYZE
                val diag = systemBridge.getRealSystemDiagnostic()

                val replyText = when {
                    lower.contains("battery") || lower.contains("बैटरी") || lower.contains("चार्ज") -> {
                        val chargingText = if (diag.isCharging) "और फ़ोन ${diag.powerSource} से चार्ज हो रहा है।" else "और बैटरी पर चल रहा है।"
                        val tempText = if (diag.batteryTempCelsius > 0) " (तापमान ${diag.batteryTempCelsius}°C, स्वास्थ्य: ${diag.batteryHealth})" else ""
                        "बॉस, फ़ोन की बैटरी ${diag.batteryPercent}% है, $chargingText$tempText"
                    }
                    lower.contains("storage") || lower.contains("स्टोरेज") || lower.contains("जगह") || lower.contains("space") -> {
                        "बॉस, फ़ोन में ${String.format(Locale.US, "%.1f", diag.freeStorageGb)} GB स्टोरेज खाली है (कुल ${String.format(Locale.US, "%.1f", diag.totalStorageGb)} GB में से)।"
                    }
                    lower.contains("ram") || lower.contains("रैम") || lower.contains("मेमोरी") -> {
                        "बॉस, रैम (RAM): ${String.format(Locale.US, "%.1f", diag.usedRamGb)} GB उपयोग में है (कुल ${String.format(Locale.US, "%.1f", diag.totalRamGb)} GB, ${diag.ramUsedPercent}% भरा हुआ)।"
                    }
                    lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("वाईफाई") || lower.contains("नेटवर्क") || lower.contains("network") -> {
                        "बॉस, नेटवर्क स्थिति: ${diag.networkStatus}।"
                    }
                    else -> diag.toDiagnosticReport()
                }

                respond(replyText)
                addTask("Device Status: ${replyText.take(30)}", AgentType.SYSTEM)
                setAgentActive(AgentType.SYSTEM, false)
                return@launch
            }

            // 6C. General Settings Troubleshooting Fallback ("कोई प्रॉब्लम हो गया है सेटिंग में जाकर बदलाव करो")
            val isSettingsTroubleshoot = (routeDecision.route == NexusRoute.ACTION) && (
                (lower.contains("सेटिंग") && (lower.contains("बदलाव") || lower.contains("बदलो") || lower.contains("ठीक") || lower.contains("change") || lower.contains("fix"))) ||
                (lower.contains("setting") && (lower.contains("fix") || lower.contains("change") || lower.contains("reset")))
            )
            if (isSettingsTroubleshoot) {
                setAgentActive(AgentType.SYSTEM, true, "Diagnosing & Fixing Settings")
                _activeCapability.value = CapabilityNode.EXECUTE

                val replyText = when {
                    lower.contains("sound") || lower.contains("volume") || lower.contains("आवाज") || lower.contains("ध्वनि") -> {
                        systemBridge.setVolumePercent(75)
                        "बॉस, आवाज़ की समस्या ठीक कर दी गई है। मीडिया वॉल्यूम 75% पर सेट कर दिया है।"
                    }
                    lower.contains("wifi") || lower.contains("इंटरनेट") || lower.contains("वाईफाई") || lower.contains("नेट") -> {
                        val (success, msg) = systemBridge.openWifiSettings()
                        if (success) "बॉस, नेटवर्क सेटिंग्स पैनल खोल दिया गया है ताकि आप वाई-फाई कनेक्शन ठीक कर सकें।" else msg
                    }
                    lower.contains("bluetooth") || lower.contains("ब्लूटूथ") -> {
                        val (success, msg) = systemBridge.openBluetoothSettings()
                        if (success) "बॉस, ब्लूटूथ सेटिंग्स पैनल खोल दिया गया है।" else msg
                    }
                    lower.contains("display") || lower.contains("brightness") || lower.contains("स्क्रीन") || lower.contains("ब्राइटनेस") -> {
                        val (success, msg) = systemBridge.openDisplaySettings()
                        if (success) "बॉस, डिस्प्ले और ब्राइटनेस सेटिंग्स खोल दी गई हैं।" else msg
                    }
                    lower.contains("accessibility") || lower.contains("एक्सेसिबिलिटी") -> {
                        MaxAccessibilityService.openAccessibilitySettings(context)
                        "बॉस, एक्सेसिबिलिटी सेटिंग्स खोल दी गई हैं।"
                    }
                    else -> {
                        systemBridge.setVolumePercent(75)
                        systemBridge.openSystemSettings()
                        "बॉस, मैंने सिस्टम की जांच की है, ऑडियो और हार्डवेयर कनेक्टिविटी को रीसेट व ऑप्टिमाइज़ कर दिया है, और सेटिंग्स पैनल खोल दिया है ताकि आप आवश्यक बदलाव तुरंत कर सकें।"
                    }
                }
                this@MaxOrchestrator.respond(replyText, speakAloud = false)
                addTask("Settings Diagnosed & Changed", AgentType.SYSTEM)
                setAgentActive(AgentType.SYSTEM, false)
                return@launch
            }

            // 7. Screen Inspection & Real-time Camera Multimodal Vision
            if (lower.contains("camera") || lower.contains("कैमरा") || lower.contains("सामने क्या है") || lower.contains("what is in front") || lower.contains("dekho") || lower.contains("देखो") || lower.contains("look at this")) {
                setAgentActive(AgentType.SYSTEM, true, "Camera Vision Analysis")
                _activeCapability.value = CapabilityNode.ANALYZE
                val frame = multimodalEngine.captureCurrentFrame()
                if (frame != null) {
                    val result = geminiService.generateMultimodalResponseWithDebug(
                        userPrompt = "User asked: '$command'. Describe or answer what you see through the camera.",
                        bitmap = frame
                    )
                    if (result.isSuccess) {
                        respond("कैमरा विज़न एनालिसिस: ${result.text}")
                        addTask("Multimodal Vision Query", AgentType.SYSTEM)
                    } else {
                        val event = result.debugEvent
                        val feedback = buildString {
                            append("⚠️ जेमिनी विज़न एनालिसिस त्रुटि [${event.errorCode.httpCode} ${event.errorCode.shortTitle}]:\n")
                            append(event.rawErrorMessage ?: event.errorCode.standardDescription)
                            append("\n\n🔄 सक्रिय फॉलबैक: ${event.fallbackState.label} (${event.fallbackState.technicalSummary})")
                        }
                        respond(feedback, speakAloud = false)
                        addTask("Vision Analysis Error: ${event.errorCode.shortTitle}", AgentType.SYSTEM)
                    }
                } else {
                    respond("बॉस, रियल-टाइम कैमरा विज़न पाइपलाइन तैयार है। विज़न स्क्रीन खोलकर लाइव स्ट्रीम शुरू करें या अनुमति दें।")
                    addTask("Camera Stream Check", AgentType.SYSTEM)
                }
                setAgentActive(AgentType.SYSTEM, false)
                return@launch
            }

            if (lower.contains("screen") || lower.contains("स्क्रीन") || lower.contains("दिख रहा")) {
                setAgentActive(AgentType.SYSTEM, true, "Reading Screen")
                _activeCapability.value = CapabilityNode.ANALYZE
                val screenContent = MaxAccessibilityService.captureCurrentScreenText()
                addTask("Screen Inspection", AgentType.SYSTEM)

                if (screenContent.startsWith("Accessibility Service is not active")) {
                    respond("बॉस, स्क्रीन देखने के लिए Accessibility Service चालू करनी होगी। सेटिंग्स खोल रहा हूँ।")
                    MaxAccessibilityService.openAccessibilitySettings(context)
                } else {
                    // Send to Gemini for intelligent summary with debugging listener
                    val prompt = "User asked about screen: '$command'. Here is what is on the screen:\n$screenContent\nSummarize briefly in Hindi."
                    val result = geminiService.generateResponseWithDebug(prompt)
                    if (result.isSuccess) {
                        respond("स्क्रीन पर यह लिखा है: ${result.text}")
                    } else {
                        val event = result.debugEvent
                        val feedback = buildString {
                            append("⚠️ जेमिनी स्क्रीन एनालिसिस त्रुटि [${event.errorCode.httpCode} ${event.errorCode.shortTitle}]:\n")
                            append(event.rawErrorMessage ?: event.errorCode.standardDescription)
                            append("\n\n🔄 सक्रिय फॉलबैक: ${event.fallbackState.label}")
                        }
                        respond(feedback, speakAloud = false)
                        addTask("Screen Analysis Error: ${event.errorCode.shortTitle}", AgentType.SYSTEM)
                    }
                }
                setAgentActive(AgentType.SYSTEM, false)
                return@launch
            }

            // 8. Navigation & Gestures (Home / Back / Recents / Scroll / Click / Type)
            if (lower.contains("home") || lower.contains("होम") || lower.contains("main screen")) {
                val success = MaxAccessibilityService.performHome()
                respond(if (success) "होम स्क्रीन पर चले गए।" else "होम एक्शन के लिए Accessibility चालू करें।")
                addTask("Global Action: Home", AgentType.SYSTEM)
                return@launch
            }
            if (lower.contains("back") || lower.contains("बैक") || lower.contains("वापस")) {
                val success = MaxAccessibilityService.performBack()
                respond(if (success) "बैक कर दिया।" else "बैक एक्शन निष्पादित किया।")
                addTask("Global Action: Back", AgentType.SYSTEM)
                return@launch
            }
            if (lower.contains("recents") || lower.contains("recent apps") || lower.contains("रिसेंट")) {
                val success = MaxAccessibilityService.performRecents()
                respond(if (success) "रिसेंट ऐप्स खोल दिए हैं।" else "Accessibility चालू करें।")
                addTask("Global Action: Recents", AgentType.SYSTEM)
                return@launch
            }
            if (lower.contains("notification") || lower.contains("नोटिफिकेशन") || lower.contains("सूचनाएं")) {
                val lastNotif = MaxAccessibilityService.lastNotification.value
                if (lower.contains("last") || lower.contains("latest") || lower.contains("हालिया") || lower.contains("क्या आया")) {
                    if (lastNotif != null) {
                        respond("हालिया नोटिफिकेशन ${lastNotif.packageName} से आया है: ${lastNotif.title} - ${lastNotif.text}")
                    } else {
                        respond("अभी कोई नया नोटिफिकेशन रिकॉर्ड नहीं हुआ है बॉस।")
                    }
                } else {
                    val success = MaxAccessibilityService.performNotifications()
                    respond(if (success) "नोटिफिकेशन पैनल खोल दिया है।" else "Accessibility चालू करें।")
                }
                addTask("Notification Action", AgentType.SYSTEM)
                return@launch
            }
            if (lower.contains("quick settings") || lower.contains("क्विक सेटिंग्स")) {
                val success = MaxAccessibilityService.performQuickSettings()
                respond(if (success) "क्विक सेटिंग्स खोल दी हैं।" else "Accessibility चालू करें।")
                addTask("Global Action: Quick Settings", AgentType.SYSTEM)
                return@launch
            }
            if (lower.contains("screenshot") || lower.contains("स्क्रीनशॉट")) {
                val success = MaxAccessibilityService.takeScreenshotAsync()
                respond(if (success) "स्क्रीनशॉट ले लिया गया है।" else "स्क्रीनशॉट लेने के लिए Accessibility अनुमति की आवश्यकता है।")
                addTask("Action: Take Screenshot", AgentType.SYSTEM)
                return@launch
            }
            if (lower.contains("scroll down") || lower.contains("नीचे स्क्रॉल") || lower.contains("स्क्रॉल करो") || lower.contains("scroll")) {
                val success = MaxAccessibilityService.scrollDown()
                respond(if (success) "स्क्रीन नीचे स्क्रॉल कर दी है।" else "जेस्चर निष्पादित करने के लिए Accessibility चालू करें।")
                addTask("Gesture: Scroll Down", AgentType.SYSTEM)
                return@launch
            }
            if (lower.contains("scroll up") || lower.contains("ऊपर स्क्रॉल")) {
                val success = MaxAccessibilityService.scrollUp()
                respond(if (success) "स्क्रीन ऊपर स्क्रॉल कर दी है।" else "जेस्चर निष्पादित करने के लिए Accessibility चालू करें।")
                addTask("Gesture: Scroll Up", AgentType.SYSTEM)
                return@launch
            }
            if (lower.contains("click") || lower.contains("tap") || lower.contains("क्लिक करो") || lower.contains("दबाओ")) {
                var target = lower
                listOf("click", "tap", "on", "क्लिक करो", "दबाओ", "button", "बटन").forEach {
                    target = target.replace(it, "")
                }
                target = target.trim().replace("\"", "")
                if (target.isNotEmpty()) {
                    val success = MaxAccessibilityService.clickNodeWithText(target)
                    respond(if (success) "'$target' पर क्लिक कर दिया है।" else "'$target' स्क्रीन पर नहीं मिला।")
                    addTask("Interaction: Click '$target'", AgentType.SYSTEM)
                    return@launch
                }
            }
            if (lower.contains("type") || lower.contains("लिखो") || lower.contains("enter text")) {
                var text = lower
                listOf("type", "लिखो", "enter text", "in", "में", "into").forEach {
                    text = text.replace(it, "")
                }
                text = text.trim().replace("\"", "")
                if (text.isNotEmpty()) {
                    val success = MaxAccessibilityService.inputText(text)
                    respond(if (success) "टेक्स्ट टाइप कर दिया: '$text'" else "कोई एक्टिव इनपुट फ़ील्ड नहीं मिला।")
                    addTask("Interaction: Type '$text'", AgentType.SYSTEM)
                    return@launch
                }
            }

            // 9. App Launching (Generic or Specific)
            val launchKeywords = listOf("open", "launch", "kholo", "खोलो", "चालू करो", "start")
            val isLaunchIntent = launchKeywords.any { lower.contains(it) }
            if (isLaunchIntent) {
                var targetApp = lower
                for (kw in launchKeywords) {
                    targetApp = targetApp.replace(kw, "")
                }
                targetApp = targetApp.trim()
                if (targetApp.isNotEmpty()) {
                    setAgentActive(AgentType.SYSTEM, true, "Opening App")
                    _activeCapability.value = CapabilityNode.EXECUTE
                    val (success, status) = systemBridge.launchAppByNameOrPackage(targetApp)
                    respond(if (success) "$targetApp खोल दिया है बॉस।" else status)
                    addTask("Launch App: $targetApp", AgentType.SYSTEM)
                    setAgentActive(AgentType.SYSTEM, false)
                    return@launch
                }
            }

            // 10. Web Search
            if (lower.contains("search") || lower.contains("google") || lower.contains("सर्च") || lower.contains("ढूंढो")) {
                setAgentActive(AgentType.BROWSER, true, "Web Search")
                _activeCapability.value = CapabilityNode.SEARCH
                var query = lower.replace("search", "").replace("google", "").replace("सर्च", "").replace("ढूंढो", "").trim()
                if (query.isEmpty()) query = "latest tech news"
                systemBridge.openWebSearch(query)
                respond("बॉस, $query के लिए वेब सर्च खोल दिया है।")
                addTask("Search Web: $query", AgentType.BROWSER)
                setAgentActive(AgentType.BROWSER, false)
                return@launch
            }

            // 11. General AI Intelligence: 100% Offline Ollama (Llama 3 8B) / Embedded Neural Brain / Gemini
            setAgentActive(AgentType.RESEARCH, true, "AI Reasoning")
            _activeCapability.value = CapabilityNode.THINK
            val diag = systemBridge.getRealSystemDiagnostic()
            val contextInfo = "Device: ${diag.model}, Android ${diag.androidVersion}, Battery: ${diag.batteryPercent}%, Power: ${diag.powerSource}, RAM: ${String.format(java.util.Locale.US, "%.1f", diag.usedRamGb)}/${String.format(java.util.Locale.US, "%.1f", diag.totalRamGb)}GB, Storage: ${String.format(java.util.Locale.US, "%.1f", diag.freeStorageGb)}GB free, Network: ${diag.networkStatus}"

            val aiResponse = when (_aiEngineMode.value) {
                AiEngineMode.JARVIS_MULTI_AI -> {
                    val multiResult = multiAiOrchestrator.executeResilientTurn(command, contextInfo)
                    val header = if (multiResult.failoverOccurred) {
                        "🔄 [Auto Failover: ${multiResult.modelId} (${multiResult.provider.name}) | ${multiResult.latencyMs}ms]\n\n"
                    } else {
                        "⚡ [${multiResult.modelId} | ${multiResult.latencyMs}ms]\n\n"
                    }
                    val reviewSuffix = if (multiResult.reviewNotes != null) {
                        "\n\n🛡️ [Reviewer: ${multiResult.reviewNotes}]"
                    } else ""
                    header + multiResult.text + reviewSuffix
                }
                AiEngineMode.GEMINI_CLOUD -> {
                    val geminiResult = geminiService.generateResponseWithDebug(command, contextInfo)
                    if (geminiResult.isSuccess) {
                        geminiResult.text
                    } else {
                        val event = geminiResult.debugEvent
                        // Try TokenRa fallback if multi-ai orchestrator auto-failover is enabled
                        if (multiAiOrchestrator.autoFailover.value && multiAiOrchestrator.tokenRaApiKey.value.isNotBlank()) {
                            val multiResult = multiAiOrchestrator.executeResilientTurn(command, contextInfo)
                            "⚠️ [GEMINI ERROR ${event.errorCode.httpCode}: ${event.errorCode.shortTitle}]\n" +
                            "🔄 [FAILOVER: ${multiResult.modelId} (${multiResult.provider.name})]\n\n${multiResult.text}"
                        } else {
                            when (event.fallbackState) {
                                GeminiFallbackState.OFFLINE_OLLAMA_FALLBACK -> {
                                    val offlineRes = ollamaService.generateResponse(command, contextInfo)
                                    if (offlineRes.isNotBlank()) {
                                        "⚠️ [GEMINI ERROR ${event.errorCode.httpCode}: ${event.errorCode.shortTitle}]\n" +
                                        "🔄 [FALLBACK: ${event.fallbackState.label}]\n\n$offlineRes"
                                    } else {
                                        val neuralFallback = nlpBrain.generatePlayfulOfflineReply(command, _isPlayfulMode.value)
                                        "⚠️ [GEMINI ERROR ${event.errorCode.httpCode}: ${event.errorCode.shortTitle}]\n" +
                                        "🔄 [FALLBACK: Embedded Neural Brain]\n\n$neuralFallback"
                                    }
                                }
                                GeminiFallbackState.EMBEDDED_NEURAL_BRAIN_FALLBACK -> {
                                    val neuralFallback = nlpBrain.generatePlayfulOfflineReply(command, _isPlayfulMode.value)
                                    "⚠️ [GEMINI ERROR ${event.errorCode.httpCode}: ${event.errorCode.shortTitle}]\n" +
                                    "🔄 [FALLBACK: Embedded Neural Brain]\n\n$neuralFallback"
                                }
                                else -> {
                                    val neuralFallback = nlpBrain.generatePlayfulOfflineReply(command, _isPlayfulMode.value)
                                    "⚠️ [GEMINI ERROR ${event.errorCode.httpCode}: ${event.errorCode.shortTitle}] ${event.rawErrorMessage}\n" +
                                    "🔄 [FALLBACK: Embedded Neural Brain]\n\n$neuralFallback"
                                }
                            }
                        }
                    }
                }
                AiEngineMode.OLLAMA_OFFLINE -> {
                    ollamaService.generateResponse(command, contextInfo)
                }
                AiEngineMode.EMBEDDED_NEURAL -> {
                    nlpBrain.generatePlayfulOfflineReply(command, _isPlayfulMode.value)
                }
            }

            respond(aiResponse)
            addTask("AI Reasoning: ${_aiEngineMode.value.name}", AgentType.RESEARCH)
            setAgentActive(AgentType.RESEARCH, false)
        }
    }

    fun respond(text: String, speakAloud: Boolean = true) {
        addMessage("MAX", text)
        if (speakAloud) {
            _voiceState.value = VoiceState.SPEAKING
            _activeCapability.value = CapabilityNode.EXECUTE
            if (_isEdgeTTSActive.value) {
                edgeTTS.speak(text)
            } else {
                textToSpeech.speak(text, _language.value)
            }
        } else {
            _voiceState.value = VoiceState.IDLE
            _activeCapability.value = CapabilityNode.NONE
        }
    }

    fun respondSpeechOnly(text: String) {
        _voiceState.value = VoiceState.SPEAKING
        _activeCapability.value = CapabilityNode.EXECUTE
        if (_isEdgeTTSActive.value) {
            edgeTTS.speak(text)
        } else {
            textToSpeech.speak(text, _language.value)
        }
    }

    suspend fun executeCanonicalUserQuery(query: String): String {
        val routeDecision = queryRouter.route(query)
        val lower = query.lowercase(Locale.getDefault())

        if (routeDecision.route == NexusRoute.DEVICE_STATUS) {
            val diag = systemBridge.getRealSystemDiagnostic()
            return when {
                lower.contains("battery") || lower.contains("बैटरी") || lower.contains("चार्ज") -> {
                    val chargingText = if (diag.isCharging) "और फ़ोन ${diag.powerSource} से चार्ज हो रहा है।" else "और बैटरी पर चल रहा है।"
                    val tempText = if (diag.batteryTempCelsius > 0) " (तापमान ${diag.batteryTempCelsius}°C, स्वास्थ्य: ${diag.batteryHealth})" else ""
                    "बॉस, फ़ोन की बैटरी ${diag.batteryPercent}% है, $chargingText$tempText"
                }
                lower.contains("storage") || lower.contains("स्टोरेज") || lower.contains("जगह") || lower.contains("space") -> {
                    "बॉस, फ़ोन में ${String.format(Locale.US, "%.1f", diag.freeStorageGb)} GB स्टोरेज खाली है (कुल ${String.format(Locale.US, "%.1f", diag.totalStorageGb)} GB में से)।"
                }
                lower.contains("ram") || lower.contains("रैम") || lower.contains("मेमोरी") -> {
                    "बॉस, रैम (RAM): ${String.format(Locale.US, "%.1f", diag.usedRamGb)} GB उपयोग में है (कुल ${String.format(Locale.US, "%.1f", diag.totalRamGb)} GB, ${diag.ramUsedPercent}% भरा हुआ)।"
                }
                lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("वाईफाई") || lower.contains("नेटवर्क") || lower.contains("network") -> {
                    "बॉस, नेटवर्क स्थिति: ${diag.networkStatus}।"
                }
                else -> diag.toDiagnosticReport()
            }
        }

        val diag = systemBridge.getRealSystemDiagnostic()
        val contextInfo = "Device: ${diag.model}, Android ${diag.androidVersion}, Battery: ${diag.batteryPercent}%, Power: ${diag.powerSource}, RAM: ${String.format(Locale.US, "%.1f", diag.usedRamGb)}/${String.format(Locale.US, "%.1f", diag.totalRamGb)}GB, Storage: ${String.format(Locale.US, "%.1f", diag.freeStorageGb)}GB free, Network: ${diag.networkStatus}"

        return when (_aiEngineMode.value) {
            AiEngineMode.JARVIS_MULTI_AI -> {
                val multiResult = multiAiOrchestrator.executeResilientTurn(query, contextInfo)
                multiResult.text
            }
            AiEngineMode.GEMINI_CLOUD -> {
                val geminiResult = geminiService.generateResponseWithDebug(query, contextInfo)
                if (geminiResult.isSuccess) {
                    geminiResult.text
                } else {
                    nlpBrain.generatePlayfulOfflineReply(query, _isPlayfulMode.value)
                }
            }
            AiEngineMode.OLLAMA_OFFLINE -> {
                val offlineRes = ollamaService.generateResponse(query, contextInfo)
                if (offlineRes.isNotBlank()) offlineRes else nlpBrain.generatePlayfulOfflineReply(query, _isPlayfulMode.value)
            }
            AiEngineMode.EMBEDDED_NEURAL -> {
                nlpBrain.generatePlayfulOfflineReply(query, _isPlayfulMode.value)
            }
        }
    }

    private fun extractPhoneNumber(cmd: String): String? {
        val regex = Regex("\\b(\\+?\\d{10,12})\\b")
        return regex.find(cmd)?.value
    }

    private fun extractMessageText(cmd: String): String {
        val prefixes = listOf("bhejo", "send", "लिखो", "भेजो", "message", "to", "par")
        var result = cmd
        for (p in prefixes) {
            val idx = result.indexOf(p, ignoreCase = true)
            if (idx != -1) {
                result = result.substring(idx + p.length).trim()
            }
        }
        return result.trim().replace("\"", "")
    }

    fun destroy() {
        wakeWordDetector.release()
        speechToText.destroy()
        textToSpeech.destroy()
        edgeTTS.release()
    }
}
