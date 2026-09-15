package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ai.MaxOrchestrator
import com.example.permission.CentralPermissionsManager
import com.example.service.MaxAccessibilityService
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.DiagnosticsDialog
import com.example.ui.components.ManageSystemSettingsDialog
import com.example.ui.components.NavigationDestination
import com.example.ui.components.NexusBottomNavigation
import com.example.ui.components.PhantomNexusPatchDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.components.SidebarDrawerContent
import com.example.ui.components.StartupPermissionsDialog
import com.example.ui.screens.AdminProvidersScreen
import com.example.ui.screens.AgentsScreen
import com.example.ui.screens.ConsoleScreen
import com.example.ui.screens.FuturisticModuleScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MemoryWalletScreen
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var orchestrator: MaxOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        orchestrator = MaxOrchestrator(this, lifecycleScope)

        setContent {
            MyApplicationTheme {
                MainAppContent(orchestrator = orchestrator)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        orchestrator.destroy()
    }
}

@Composable
fun MainAppContent(orchestrator: MaxOrchestrator) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var currentDestination by remember { mutableStateOf(NavigationDestination.HOME) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showManageSystemSettings by remember { mutableStateOf(false) }
    var showPatchDetectDialog by remember { mutableStateOf(false) }
    var showStartupPermissionsDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    // UpdateChecker status targeting v1.3.5
    val isUpdateRequired by orchestrator.updateChecker.isUpdateRequired.collectAsState()

    // State collections
    val voiceState by orchestrator.voiceState.collectAsState()
    val activeCapability by orchestrator.activeCapability.collectAsState()
    val liveTranscript by orchestrator.liveTranscript.collectAsState()
    val rmsLevel by orchestrator.rmsVolume.collectAsState()
    val tasks by orchestrator.tasks.collectAsState()
    val messages by orchestrator.messages.collectAsState()
    val agents by orchestrator.agents.collectAsState()
    val language by orchestrator.language.collectAsState()
    val isHandsFree by orchestrator.isHandsFree.collectAsState()
    val pendingAction by orchestrator.pendingAction.collectAsState()
    val isAccessibilityActive by MaxAccessibilityService.isConnected.collectAsState()

    // Advanced Neural & Voice Engine States
    val aiEngineMode by orchestrator.aiEngineMode.collectAsState()
    val isPlayfulMode by orchestrator.isPlayfulMode.collectAsState()
    val isEdgeTTSActive by orchestrator.isEdgeTTSActive.collectAsState()
    val isOllamaConnected by orchestrator.ollamaService.isOllamaConnected.collectAsState()
    var currentEdgeVoice by remember { mutableStateOf(orchestrator.edgeTTS.currentVoice) }

    val deviceSpecs = remember { orchestrator.systemBridge.getDeviceSpecs() }

    // Permission launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true && isHandsFree) {
            orchestrator.wakeWordDetector.startWakeWordListening()
        }
        // Check if there are still missing core hardware permissions (Microphone, Accessibility, Overlay)
        if (CentralPermissionsManager.hasMissingCorePermissions(context)) {
            showStartupPermissionsDialog = true
        }
    }

    val requestAllPermissions = {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CAMERA
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            perms.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsLauncher.launch(perms.toTypedArray())
    }

    // Startup Hardware Permission Check:
    // Prompt upfront runtime permissions and detect missing critical hardware access (Microphone, Accessibility, Overlay)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        requestAllPermissions()
        if (isHandsFree && orchestrator.wakeWordDetector.hasPermission()) {
            orchestrator.wakeWordDetector.startWakeWordListening()
        }
        // Evaluate central permissions manager for missing hardware access
        if (CentralPermissionsManager.hasMissingCorePermissions(context)) {
            showStartupPermissionsDialog = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.Transparent,
                drawerContentColor = Color.White
            ) {
                SidebarDrawerContent(
                    currentDestination = currentDestination,
                    deviceSpecs = deviceSpecs,
                    onDestinationSelected = { dest ->
                        when (dest) {
                            NavigationDestination.SETTINGS -> {
                                showSettingsDialog = true
                            }
                            NavigationDestination.PERMISSIONS -> {
                                showStartupPermissionsDialog = true
                            }
                            else -> {
                                currentDestination = dest
                            }
                        }
                    },
                    onClose = { coroutineScope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = DeepSpaceBlack,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NexusBottomNavigation(
                    modifier = Modifier.navigationBarsPadding(),
                    currentDestination = currentDestination,
                    onDestinationSelected = { dest ->
                        if (dest == NavigationDestination.SETTINGS) {
                            showSettingsDialog = true
                        } else {
                            currentDestination = dest
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                when (currentDestination) {
                    NavigationDestination.HOME,
                    NavigationDestination.SYSTEM,
                    NavigationDestination.FILES -> {
                        HomeScreen(
                            orchestrator = orchestrator,
                            voiceState = voiceState,
                            activeCapability = activeCapability,
                            liveTranscript = liveTranscript,
                            rmsLevel = rmsLevel,
                            tasks = tasks,
                            latestMessage = messages.lastOrNull { it.sender == "MAX" }?.text
                                ?: "MAX Core Ready.",
                            language = language,
                            messages = messages,
                            onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                            onOpenSettings = { showSettingsDialog = true },
                            onOpenManageSystemSettings = { showManageSystemSettings = true },
                            onOpenPatchDetect = { showPatchDetectDialog = true },
                            onOpenPermissionsManager = { showStartupPermissionsDialog = true },
                            onOpenDiagnostics = { showDiagnosticsDialog = true }
                        )
                    }

                    NavigationDestination.CONSOLE -> {
                        ConsoleScreen(
                            orchestrator = orchestrator,
                            messages = messages,
                            voiceState = voiceState,
                            onBack = { currentDestination = NavigationDestination.HOME }
                        )
                    }

                    NavigationDestination.MEMORY -> {
                        MemoryWalletScreen(
                            orchestrator = orchestrator,
                            onBack = { currentDestination = NavigationDestination.HOME }
                        )
                    }

                    NavigationDestination.AGENTS,
                    NavigationDestination.TASKS -> {
                        AgentsScreen(
                            agents = agents,
                            tasks = tasks,
                            onBack = { currentDestination = NavigationDestination.HOME }
                        )
                    }

                    NavigationDestination.BROWSER -> {
                        HomeScreen(
                            orchestrator = orchestrator,
                            voiceState = voiceState,
                            activeCapability = activeCapability,
                            liveTranscript = liveTranscript,
                            rmsLevel = rmsLevel,
                            tasks = tasks,
                            latestMessage = "वेब ब्राउज़र और सर्च रेडी है। क्या ढूँढना चाहते हैं?",
                            language = language,
                            onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                            onOpenSettings = { showSettingsDialog = true },
                            onOpenManageSystemSettings = { showManageSystemSettings = true },
                            onOpenPatchDetect = { showPatchDetectDialog = true },
                            onOpenPermissionsManager = { showStartupPermissionsDialog = true }
                        )
                    }

                    NavigationDestination.SETTINGS -> {
                        // Settings shown as dialog
                        HomeScreen(
                            orchestrator = orchestrator,
                            voiceState = voiceState,
                            activeCapability = activeCapability,
                            liveTranscript = liveTranscript,
                            rmsLevel = rmsLevel,
                            tasks = tasks,
                            latestMessage = messages.lastOrNull { it.sender == "MAX" }?.text ?: "",
                            language = language,
                            onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                            onOpenSettings = { showSettingsDialog = true },
                            onOpenManageSystemSettings = { showManageSystemSettings = true },
                            onOpenPatchDetect = { showPatchDetectDialog = true },
                            onOpenPermissionsManager = { showStartupPermissionsDialog = true }
                        )
                    }

                    NavigationDestination.PERMISSIONS -> {
                        // When navigated to PERMISSIONS, render Home behind and open the dialog
                        HomeScreen(
                            orchestrator = orchestrator,
                            voiceState = voiceState,
                            activeCapability = activeCapability,
                            liveTranscript = liveTranscript,
                            rmsLevel = rmsLevel,
                            tasks = tasks,
                            latestMessage = "Hardware Permissions Hub",
                            language = language,
                            onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                            onOpenSettings = { showSettingsDialog = true },
                            onOpenManageSystemSettings = { showManageSystemSettings = true },
                            onOpenPatchDetect = { showPatchDetectDialog = true },
                            onOpenPermissionsManager = { showStartupPermissionsDialog = true }
                        )
                    }

                    NavigationDestination.PROVIDERS -> {
                        AdminProvidersScreen(
                            orchestratorManager = orchestrator.multiAiOrchestrator,
                            onBack = { currentDestination = NavigationDestination.HOME }
                        )
                    }

                    NavigationDestination.VISION,
                    NavigationDestination.AUTOMATIONS,
                    NavigationDestination.DEVICES,
                    NavigationDestination.NEWS,
                    NavigationDestination.DEVELOPER -> {
                        FuturisticModuleScreen(
                            destination = currentDestination,
                            onBack = { currentDestination = NavigationDestination.HOME },
                            orchestrator = orchestrator
                        )
                    }
                }

                // Security Confirmation Dialog for High Risk Actions (Call, SMS, WhatsApp)
                pendingAction?.let { action ->
                    ConfirmationDialog(
                        pendingAction = action,
                        onDismiss = { orchestrator.clearPendingAction() }
                    )
                }

                // Settings Dialog
                if (showSettingsDialog) {
                    androidx.compose.ui.platform.LocalContext.current.let { ctx ->
                        SettingsDialog(
                            context = ctx,
                            currentLanguage = language,
                            isHandsFree = isHandsFree,
                            isAccessibilityActive = isAccessibilityActive,
                            aiEngineMode = aiEngineMode,
                            isPlayfulMode = isPlayfulMode,
                            isEdgeTTSActive = isEdgeTTSActive,
                            currentEdgeVoice = currentEdgeVoice,
                            ollamaHost = orchestrator.ollamaService.hostUrl,
                            isOllamaConnected = isOllamaConnected,
                            onLanguageChange = { orchestrator.setLanguage(it) },
                            onHandsFreeToggle = { orchestrator.toggleHandsFree() },
                            onAiEngineModeChange = { orchestrator.setAiEngineMode(it) },
                            onPlayfulModeToggle = { orchestrator.setPlayfulMode(it) },
                            onEdgeTTSToggle = { orchestrator.setEdgeTTSActive(it) },
                            onEdgeVoiceChange = {
                                orchestrator.setEdgeVoice(it)
                                currentEdgeVoice = it
                            },
                            onOllamaHostChange = { orchestrator.ollamaService.hostUrl = it },
                            onTestOllamaConnection = {
                                coroutineScope.launch {
                                    orchestrator.ollamaService.checkHealth()
                                }
                            },
                            onApiKeySaved = { orchestrator.updateCustomApiKey(it) },
                            onRequestPermissions = requestAllPermissions,
                            onOpenManageSystemSettings = { showManageSystemSettings = true },
                            onOpenPatchDetect = { showPatchDetectDialog = true },
                            onOpenPermissionsManager = { showStartupPermissionsDialog = true },
                            onOpenProvidersAdmin = {
                                showSettingsDialog = false
                                currentDestination = NavigationDestination.PROVIDERS
                            },
                            onEmergencyStop = { orchestrator.emergencyStop() },
                            onDismiss = { showSettingsDialog = false }
                        )
                    }
                }

                // Central Hardware Permissions Manager Dialog (Detects missing Microphone, Accessibility, Overlay at startup)
                if (showStartupPermissionsDialog) {
                    androidx.compose.ui.platform.LocalContext.current.let { ctx ->
                        StartupPermissionsDialog(
                            context = ctx,
                            onRequestMicrophoneRuntime = {
                                permissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                            },
                            onDismiss = {
                                showStartupPermissionsDialog = false
                                if (currentDestination == NavigationDestination.PERMISSIONS) {
                                    currentDestination = NavigationDestination.HOME
                                }
                            }
                        )
                    }
                }

                // Manage System Settings Dialog (Permissions & Hardware Control Hub)
                if (showManageSystemSettings) {
                    androidx.compose.ui.platform.LocalContext.current.let { ctx ->
                        ManageSystemSettingsDialog(
                            context = ctx,
                            orchestrator = orchestrator,
                            onRequestMicrophone = {
                                permissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                            },
                            onRequestPhonePermissions = {
                                val perms = mutableListOf(
                                    Manifest.permission.CALL_PHONE,
                                    Manifest.permission.READ_PHONE_STATE
                                )
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    perms.add(Manifest.permission.ANSWER_PHONE_CALLS)
                                }
                                permissionsLauncher.launch(perms.toTypedArray())
                            },
                            onDismiss = { showManageSystemSettings = false }
                        )
                    }
                }

                // Phantom Nexus System Patch Detect v1.3.5 Dialog (Enforced by UpdateChecker)
                val shouldShowPatchModal = isUpdateRequired || showPatchDetectDialog
                if (shouldShowPatchModal) {
                    PhantomNexusPatchDialog(
                        isUpdateRequired = isUpdateRequired,
                        onResolvePatch = {
                            orchestrator.updateChecker.resolvePatch()
                            showPatchDetectDialog = false
                        },
                        onDismiss = {
                            if (!isUpdateRequired) {
                                showPatchDetectDialog = false
                            }
                        },
                        onManageSystemSettings = {
                            showManageSystemSettings = true
                        }
                    )
                }

                // Pantham Nexus Internal Subsystem Diagnostics Dialog
                if (showDiagnosticsDialog) {
                    DiagnosticsDialog(
                        onDismiss = { showDiagnosticsDialog = false }
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(text = "Hello $name!", modifier = modifier)
}
