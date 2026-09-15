package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import com.pantham.nexus.voice.NexusMicController
import com.pantham.nexus.voice.MicState
import com.pantham.nexus.ui.components.NexusRealMicButton
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import android.util.Log
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import com.example.ai.CapabilityNode
import com.example.ai.ConsoleMessage
import com.example.ai.MaxOrchestrator
import com.example.ai.TaskItem
import com.example.ui.components.FuturisticVoicePanel
import com.example.ui.components.MaxPlanetCore
import com.example.ui.components.QuickActionsSection
import com.example.ui.components.QuickConsoleCard
import com.example.ui.components.TaskTimelineCard
import com.example.ui.components.crystallineGlass
import com.example.ui.theme.AmberNeon
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletNeon
import com.example.voice.AssistantLanguage
import com.example.voice.VoiceState

/**
 * FINAL HOME UI:
 * - Futuristic AI OS Dark Space Canvas
 * - Top App Bar: Menu, PHANTOM NEXUS / MAX AI OS, Status (Online), Settings
 * - Greeting Section: "Good Evening, Boss.", "I'm MAX." (English default)
 * - Quick Actions: SCHEDULE, ANALYZE, RESEARCH, SYSTEM
 * - MAX Core: Realistic Digital Planet with Orbital Capability Nodes (Think, Execute, Search, Automate, Analyze, Learn)
 *   and animated data flow energy links
 * - Quick Console: Clean cyberpunk card with "MAX: Ready when you are."
 * - Voice Panel: Glowing circular core, waveform, state indicator, mic button
 * - Persistent Floating Voice Active Mic (Always visible on display)
 * - Command Bar: Text input, Voice Mic trigger, and Send button
 */
@Composable
fun HomeScreen(
    orchestrator: MaxOrchestrator,
    voiceState: VoiceState,
    activeCapability: CapabilityNode,
    liveTranscript: String,
    rmsLevel: Float,
    tasks: List<TaskItem>,
    latestMessage: String,
    language: AssistantLanguage,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenManageSystemSettings: () -> Unit = {},
    onOpenPatchDetect: () -> Unit = {},
    onOpenPermissionsManager: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    messages: List<ConsoleMessage> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isHandsFree by orchestrator.isHandsFree.collectAsState()

    val nexusMicController = remember {
        NexusMicController(
            context = context,
            onTextRecognized = { spokenText ->
                Log.d("NEXUS_MIC", "RESULT = $spokenText")
                orchestrator.processUserCommand(spokenText, isVoice = true)
            },
            onError = { message ->
                Log.e("NEXUS_MIC", "Recognition error = $message")
                orchestrator.addErrorMessage(message)
            }
        )
    }

    DisposableEffect(Unit) {
        nexusMicController.initialize()
        onDispose {
            nexusMicController.destroy()
        }
    }

    val micState by nexusMicController.state.collectAsState()
    val partialText by nexusMicController.partialText.collectAsState()

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("NEXUS_MIC", "permission result = $granted")
        if (granted) {
            orchestrator.stopTts()
            orchestrator.wakeWordDetector.pauseForCommand()
            orchestrator.wakeWordDetector.stopWakeWordListening()
            nexusMicController.start()
        } else {
            Toast.makeText(context, "Microphone permission required", Toast.LENGTH_LONG).show()
        }
    }

    val onMicPressed: () -> Unit = {
        Log.d("NEXUS_MIC", "MIC BUTTON CLICKED")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val current = nexusMicController.state.value
            if (current == MicState.LISTENING || current == MicState.STARTING) {
                nexusMicController.stop()
            } else {
                orchestrator.stopTts()
                orchestrator.wakeWordDetector.pauseForCommand()
                orchestrator.wakeWordDetector.stopWakeWordListening()
                nexusMicController.start()
            }
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    var textInput by remember { mutableStateOf("") }
    val isWakeListening by orchestrator.wakeWordDetector.isListeningForWakeWord.collectAsState()
    val isWakeTriggered by orchestrator.wakeWordDetector.isWakeWordTriggered.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, liveTranscript) {
        val totalCount = messages.size + (if (liveTranscript.isNotBlank()) 1 else 0)
        if (totalCount > 0) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpaceBlack)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. TOP APP BAR
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.testTag("menu_drawer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = TextPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clickable { onOpenPatchDetect() }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(VioletNeon, CyanNeon))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("M", color = DeepSpaceBlack, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "PHANTOM NEXUS",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    letterSpacing = 1.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "v1.3.5",
                                    color = EmeraldNeon,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "MAX AI OS LAYER",
                                color = CyanNeon,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.sp,
                                letterSpacing = 1.2.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Manage System Settings Quick Action
                        IconButton(
                            onClick = onOpenManageSystemSettings,
                            modifier = Modifier.testTag("top_manage_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Manage System Settings",
                                tint = CyanNeon
                            )
                        }

                        // Central Hardware Permissions Manager Quick Action
                        IconButton(
                            onClick = onOpenPermissionsManager,
                            modifier = Modifier.testTag("top_permissions_manager_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Hardware Permissions Manager",
                                tint = AmberNeon
                            )
                        }

                        // Online Status Badge
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x2200E676))
                                .border(1.dp, EmeraldNeon.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(EmeraldNeon)
                            )
                            Text(
                                text = "Online",
                                color = EmeraldNeon,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Clear Chat History Button
                        IconButton(
                            onClick = { orchestrator.clearConversationHistory() },
                            modifier = Modifier.testTag("clear_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear Chat History",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Diagnostics Quick Action
                        IconButton(
                            onClick = onOpenDiagnostics,
                            modifier = Modifier.testTag("top_diagnostics_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Diagnostics",
                                tint = VioletNeon
                            )
                        }

                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            // 1.5 PROACTIVE SUGGESTION CARD
            item {
                val proactiveSuggestion by orchestrator.proactiveAssistant.activeSuggestion.collectAsState()
                proactiveSuggestion?.let { suggestion ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AmberNeon.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = GlassSurface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(AmberNeon.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Proactive Alert",
                                        tint = AmberNeon,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = suggestion.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = suggestion.message,
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                            IconButton(
                                onClick = { orchestrator.proactiveAssistant.dismissSuggestion() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // 2. SCROLLABLE AREA FOR CHAT HISTORY / HERO SECTION
            if (messages.isEmpty()) {
                // GREETING SECTION (English default per user request)
                item {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            text = "Good Evening, Boss.",
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "I'm MAX.",
                            color = CyanNeon,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Your AI OS Companion. Ready to assist, automate, and execute.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // QUICK ACTIONS GRID (SCHEDULE, ANALYZE, RESEARCH, SYSTEM)
                item {
                    QuickActionsSection(
                        onScheduleClick = { orchestrator.processUserCommand("Schedule reminders for today") },
                        onAnalyzeClick = { orchestrator.processUserCommand("Analyze real device and system diagnostics") },
                        onResearchClick = { orchestrator.processUserCommand("Research latest AI news") },
                        onSystemClick = { orchestrator.processUserCommand("Show device specs and battery") }
                    )
                }

                // CENTRAL HERO: MAX CORE (Realistic Digital Planet with Orbital Nodes & Energy Links)
                item {
                    MaxPlanetCore(
                        activeCapability = activeCapability,
                        onNodeClicked = { capability ->
                            when (capability) {
                                CapabilityNode.THINK -> orchestrator.processUserCommand("Think and plan my day")
                                CapabilityNode.SEARCH -> orchestrator.processUserCommand("Search web for latest news")
                                CapabilityNode.ANALYZE -> orchestrator.processUserCommand("Analyze real device and system diagnostics")
                                CapabilityNode.EXECUTE -> orchestrator.processUserCommand("Open WhatsApp")
                                CapabilityNode.AUTOMATE -> orchestrator.processUserCommand("Check battery and optimize volume")
                                CapabilityNode.LEARN -> orchestrator.processUserCommand("What are your core capabilities?")
                                CapabilityNode.NONE -> {}
                            }
                        }
                    )
                }

                // Empty state hint card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
                        colors = CardDefaults.cardColors(containerColor = GlassSurface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x3300E5FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text(
                                    text = "TAP CENTRAL FAB TO SPEAK",
                                    color = CyanNeon,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Speak aloud in Hindi or English, or type below to chat with MAX.",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            } else {
                // CHAT SESSION HEADER CHIP
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldNeon)
                            )
                            Text(
                                text = "MAX DIALOGUE SESSION",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "${messages.size} messages",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // SCROLLABLE CHAT MESSAGES
                items(messages, key = { it.id }) { msg ->
                    ChatMessageBubble(
                        message = msg,
                        onReplay = { text ->
                            orchestrator.respond(text, speakAloud = true)
                        },
                        onDelete = {
                            orchestrator.deleteMessage(msg.id)
                        },
                        onDeepDive = { text ->
                            orchestrator.processUserCommand("Explain this in more detail: $text", isVoice = false)
                        }
                    )
                }
            }

            // LIVE TRANSCRIPT STREAMING BUBBLE
            val displayTranscript = if (partialText.isNotBlank()) partialText else liveTranscript
            val isListeningActive = micState == MicState.LISTENING || micState == MicState.STARTING || voiceState == VoiceState.LISTENING
            if (isListeningActive && displayTranscript.isNotBlank()) {
                item {
                    LiveTranscriptBubble(
                        liveTranscript = displayTranscript,
                        rmsLevel = rmsLevel
                    )
                }
            }

            // THINKING INDICATOR
            if (micState == MicState.PROCESSING || voiceState == VoiceState.THINKING) {
                item {
                    ThinkingIndicator()
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // 8. DOCKED BOTTOM CONTROLS: REAL CANONICAL MIC BUTTON + COMMAND INPUT BAR
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            DeepSpaceBlack.copy(alpha = 0.85f),
                            DeepSpaceBlack
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when (micState) {
                            MicState.STARTING, MicState.LISTENING -> CyanNeon.copy(alpha = 0.95f)
                            MicState.PROCESSING -> AmberNeon.copy(alpha = 0.95f)
                            MicState.ERROR -> CrimsonNeon.copy(alpha = 0.85f)
                            MicState.IDLE -> if (voiceState == VoiceState.SPEAKING) EmeraldNeon.copy(alpha = 0.95f) else Color(0xEE0B1220)
                        }
                    )
                    .border(
                        1.dp,
                        when (micState) {
                            MicState.STARTING, MicState.LISTENING -> Color.White
                            MicState.PROCESSING -> AmberNeon
                            MicState.ERROR -> CrimsonNeon
                            MicState.IDLE -> if (voiceState == VoiceState.SPEAKING) EmeraldNeon else CyanNeon.copy(alpha = 0.4f)
                        },
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = when (micState) {
                        MicState.STARTING -> "● STARTING MIC..."
                        MicState.LISTENING -> "● LISTENING..."
                        MicState.PROCESSING -> "⚡ PROCESSING..."
                        MicState.ERROR -> "⚠️ MIC ERROR"
                        MicState.IDLE -> if (voiceState == VoiceState.SPEAKING) "🔊 MAX SPEAKING" else "TAP TO SPEAK"
                    },
                    color = when (micState) {
                        MicState.STARTING, MicState.LISTENING, MicState.PROCESSING -> DeepSpaceBlack
                        MicState.ERROR -> Color.White
                        MicState.IDLE -> if (voiceState == VoiceState.SPEAKING) DeepSpaceBlack else CyanNeon
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp
                )
            }

            // Real Canonical Microphone Button
            NexusRealMicButton(
                state = micState,
                onMicClick = onMicPressed,
                modifier = Modifier
                    .testTag("central_voice_fab")
                    .testTag("floating_voice_active_mic")
            )

            // Sleek Command Bar for Text Input
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .crystallineGlass(
                        shape = RoundedCornerShape(26.dp),
                        borderColor = CyanNeon.copy(alpha = 0.5f),
                        glowAccent = CyanNeon,
                        specularGleam = true,
                        noiseDensity = 1.0f,
                        alphaSubstrate = 0.92f
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = {
                            Text(
                                "Ask MAX... / प्रश्न पूछें...",
                                color = TextMuted,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("command_input_field"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (textInput.isNotBlank()) {
                                    orchestrator.processUserCommand(textInput, isVoice = false)
                                    textInput = ""
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = CyanNeon
                        )
                    )

                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                orchestrator.processUserCommand(textInput, isVoice = false)
                                textInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(CyanNeon, VioletNeon))
                            )
                            .testTag("send_command_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = DeepSpaceBlack,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Prominent Central Voice FAB for real-time voice interaction.
 * Powered by NexusRealMicButton.
 */
@Composable
fun CentralVoiceFab(
    voiceState: VoiceState,
    rmsLevel: Float,
    isWakeListening: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val micState = when (voiceState) {
        VoiceState.LISTENING -> MicState.LISTENING
        VoiceState.THINKING -> MicState.PROCESSING
        else -> MicState.IDLE
    }

    NexusRealMicButton(
        state = micState,
        onMicClick = onClick,
        modifier = modifier
            .testTag("central_voice_fab")
            .testTag("floating_voice_active_mic")
    )
}

/**
 * Backward-compatible alias for existing callers referencing FloatingVoiceActiveMic
 */
@Composable
fun FloatingVoiceActiveMic(
    voiceState: VoiceState,
    rmsLevel: Float,
    isWakeListening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CentralVoiceFab(
        voiceState = voiceState,
        rmsLevel = rmsLevel,
        isWakeListening = isWakeListening,
        onClick = onClick,
        modifier = modifier
    )
}

/**
 * Individual Chat Message Bubble for User & MAX AI
 * Includes touch gesture handling:
 * - Swipe left or right to dismiss/delete message
 * - Long-press for context-aware quick actions (copy text, speak aloud, deep dive, delete)
 */
@Composable
fun ChatMessageBubble(
    message: ConsoleMessage,
    onReplay: (String) -> Unit,
    onDelete: () -> Unit = {},
    onDeepDive: (String) -> Unit = {}
) {
    val isUser = message.sender == "USER"
    var showActionMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var isDismissed by remember { mutableStateOf(false) }

    if (isDismissed) return

    if (showActionMenu) {
        AlertDialog(
            onDismissRequest = { showActionMenu = false },
            title = {
                Text(
                    text = if (isUser) "User Message Actions" else "MAX AI Actions",
                    color = CyanNeon,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "\"${message.text.take(100)}${if (message.text.length > 100) "..." else ""}\"",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Option 1: Copy Text
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(message.text))
                                Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                                showActionMenu = false
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(18.dp))
                        Text("Copy Text", color = TextPrimary, fontSize = 14.sp)
                    }

                    // Option 2: Replay / Speak Aloud
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onReplay(message.text)
                                showActionMenu = false
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = EmeraldNeon, modifier = Modifier.size(18.dp))
                        Text("Speak / Read Aloud", color = TextPrimary, fontSize = 14.sp)
                    }

                    // Option 3: Deep Dive / Explain Further (for AI messages)
                    if (!isUser) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onDeepDive(message.text)
                                    showActionMenu = false
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = VioletNeon, modifier = Modifier.size(18.dp))
                            Text("Deep Dive / Explain Further", color = TextPrimary, fontSize = 14.sp)
                        }
                    }

                    // Option 4: Dismiss / Delete Message
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showActionMenu = false
                                isDismissed = true
                                onDelete()
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = AmberNeon, modifier = Modifier.size(18.dp))
                        Text("Dismiss / Delete Message", color = AmberNeon, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showActionMenu = false }) {
                    Text("Close", color = TextMuted)
                }
            },
            containerColor = DeepSpaceBlack,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            if (abs(offsetX.value) > 220f) {
                                val target = if (offsetX.value > 0) 1000f else -1000f
                                offsetX.animateTo(target, tween(200))
                                isDismissed = true
                                onDelete()
                            } else {
                                offsetX.animateTo(0f, tween(200))
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch { offsetX.animateTo(0f, tween(200)) }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        coroutineScope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount)
                        }
                    }
                )
            }
            .padding(horizontal = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Sender header & Timestamp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 3.dp, start = if (isUser) 0.dp else 4.dp, end = if (isUser) 4.dp else 0.dp)
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(CyanNeon, VioletNeon))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("M", color = DeepSpaceBlack, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
            Text(
                text = if (isUser) "YOU" else "MAX AI OS",
                color = if (isUser) VioletNeon else CyanNeon,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (message.isVoice) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x3300E5FF))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text("🎤 VOICE", color = CyanNeon, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                text = message.timestamp,
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Bubble Card with long-press context gesture
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser)
                        Brush.linearGradient(listOf(Color(0xFF2A164D), Color(0xFF1B0F33)))
                    else
                        Brush.linearGradient(listOf(Color(0xFF0E182A), Color(0xFF08101E)))
                )
                .border(
                    1.dp,
                    if (isUser) VioletNeon.copy(alpha = 0.5f) else CyanNeon.copy(alpha = 0.35f),
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .pointerInput(message.id) {
                    detectTapGestures(
                        onLongPress = {
                            showActionMenu = true
                        }
                    )
                }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = message.text,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                if (!isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { onReplay(message.text) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Listen to response",
                                tint = CyanNeon.copy(alpha = 0.75f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Real-time Live Speech Transcription Bubble
 */
@Composable
fun LiveTranscriptBubble(
    liveTranscript: String,
    rmsLevel: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 3.dp, start = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(CyanNeon)
            )
            Text(
                text = "LISTENING LIVE...",
                color = CyanNeon,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                .background(Color(0x2200E5FF))
                .border(1.dp, CyanNeon.copy(alpha = 0.6f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                .padding(12.dp)
        ) {
            Text(
                text = liveTranscript.ifBlank { "Listening..." },
                color = TextPrimary,
                fontSize = 14.sp,
                fontStyle = if (liveTranscript.isBlank()) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
            )
        }
    }
}

/**
 * Animated Thinking / Planning Indicator
 */
@Composable
fun ThinkingIndicator() {
    Row(
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x22FFB300))
            .border(1.dp, AmberNeon.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(AmberNeon)
        )
        Text(
            text = "MAX is thinking & processing...",
            color = AmberNeon,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun HomeQuickChip(
    label: String,
    icon: ImageVector,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x2200E5FF))
            .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 7.dp)
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = CyanNeon,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
