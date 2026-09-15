package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.service.MaxAccessibilityService
import com.example.ui.theme.AmberNeon
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletNeon
import com.example.voice.AssistantLanguage

import com.example.ai.AiEngineMode
import com.example.voice.EdgeVoice

@Composable
fun SettingsDialog(
    context: Context,
    currentLanguage: AssistantLanguage,
    isHandsFree: Boolean,
    isAccessibilityActive: Boolean,
    aiEngineMode: AiEngineMode,
    isPlayfulMode: Boolean,
    isEdgeTTSActive: Boolean,
    currentEdgeVoice: EdgeVoice,
    ollamaHost: String,
    isOllamaConnected: Boolean,
    onLanguageChange: (AssistantLanguage) -> Unit,
    onHandsFreeToggle: () -> Unit,
    onAiEngineModeChange: (AiEngineMode) -> Unit,
    onPlayfulModeToggle: (Boolean) -> Unit,
    onEdgeTTSToggle: (Boolean) -> Unit,
    onEdgeVoiceChange: (EdgeVoice) -> Unit,
    onOllamaHostChange: (String) -> Unit,
    onTestOllamaConnection: () -> Unit,
    onApiKeySaved: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenManageSystemSettings: () -> Unit,
    onOpenPatchDetect: () -> Unit,
    onOpenPermissionsManager: () -> Unit = {},
    onOpenProvidersAdmin: () -> Unit = {},
    onEmergencyStop: () -> Unit,
    onDismiss: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf("") }
    var keySavedMessage by remember { mutableStateOf(false) }
    var ollamaHostInput by remember { mutableStateOf(ollamaHost) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF5080E1B))
                .border(1.2.dp, CyanNeon, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MAX SYSTEM SETTINGS",
                        color = CyanNeon,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                // Manage System Settings Option (Requested by User)
                Button(
                    onClick = {
                        onDismiss()
                        onOpenManageSystemSettings()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("manage_system_settings_tile"),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DeepSpaceBlack),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "⚙️ MANAGE SYSTEM SETTINGS (सिस्टम सेटिंग्स)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // Central Hardware Permissions Manager Tile
                Button(
                    onClick = {
                        onDismiss()
                        onOpenPermissionsManager()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("central_permissions_manager_tile"),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberNeon, contentColor = DeepSpaceBlack),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "🛡️ CENTRAL PERMISSIONS MANAGER (अनुमतियां)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // Patch Detect v1.3.5 Tile
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x2200E676))
                        .border(1.dp, EmeraldNeon.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable {
                            onDismiss()
                            onOpenPatchDetect()
                        }
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "PHANTOM NEXUS PATCH DETECT", color = EmeraldNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Engine v1.3.5 Verified • Tap to inspect notes", color = TextSecondary, fontSize = 10.sp)
                        }
                        Text(text = "v1.3.5", color = EmeraldNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                // 🧠 AI Engine Selection (Offline Ollama, Neural Brain, Gemini)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "AI INFERENCE ENGINE (100% प्राइवेसी)",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    AiEngineMode.entries.forEach { mode ->
                        val isSelected = aiEngineMode == mode
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0x3300E5FF) else Color(0x15FFFFFF))
                                .border(1.dp, if (isSelected) CyanNeon else GlassBorder, RoundedCornerShape(8.dp))
                                .clickable { onAiEngineModeChange(mode) }
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = mode.displayName,
                                    color = if (isSelected) CyanNeon else TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenProvidersAdmin()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x3300E5FF), contentColor = CyanNeon),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("open_providers_admin_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Bolt, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Multi-AI Admin & Model Router", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // 🦙 Ollama Local Host Configuration
                if (aiEngineMode == AiEngineMode.OLLAMA_OFFLINE) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x2200E5FF))
                            .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "OLLAMA HOST (LOCAL DAEMON)", color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (isOllamaConnected) "🟢 CONNECTED" else "🟠 LOCAL FALLBACK ACTIVE",
                                color = if (isOllamaConnected) EmeraldNeon else Color(0xFFFFB300),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedTextField(
                            value = ollamaHostInput,
                            onValueChange = {
                                ollamaHostInput = it
                                onOllamaHostChange(it)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanNeon,
                                unfocusedBorderColor = GlassBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        Button(
                            onClick = onTestOllamaConnection,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DeepSpaceBlack),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Test Ollama Connection", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 💖 Personality: Unstoppable Playfulness (अनस्टॉपेबल नटखटपन)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x22FF4081))
                        .border(1.dp, Color(0x66FF4081), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "अनस्टॉपेबल नटखटपन & 24/7 Care",
                            color = Color(0xFFFF80AB),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Playful, affectionate, human-like teasing & loving support",
                            color = TextSecondary,
                            fontSize = 10.5.sp
                        )
                    }
                    Switch(
                        checked = isPlayfulMode,
                        onCheckedChange = onPlayfulModeToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFF4081),
                            checkedTrackColor = Color(0x66FF4081)
                        )
                    )
                }

                // 🎙️ Microsoft Edge Natural TTS & Voice Selection
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x227C4DFF))
                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Microsoft Edge Natural TTS", color = VioletNeon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Human-like pitch, breath pauses, zero robotic delay", color = TextSecondary, fontSize = 10.sp)
                        }
                        Switch(
                            checked = isEdgeTTSActive,
                            onCheckedChange = onEdgeTTSToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VioletNeon,
                                checkedTrackColor = Color(0x447C4DFF)
                            )
                        )
                    }

                    if (isEdgeTTSActive) {
                        Text(text = "SELECT NATURAL VOICE:", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        EdgeVoice.entries.forEach { voice ->
                            val isSelected = currentEdgeVoice == voice
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0x337C4DFF) else Color(0x10FFFFFF))
                                    .border(1.dp, if (isSelected) VioletNeon else Color.Transparent, RoundedCornerShape(6.dp))
                                    .clickable { onEdgeVoiceChange(voice) }
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = voice.displayName,
                                        color = if (isSelected) VioletNeon else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isSelected) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = VioletNeon, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Language Selection
                Text(text = "LANGUAGE / भाषा", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistantLanguage.entries.forEach { lang ->
                        val isSelected = currentLanguage == lang
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0x3300E5FF) else Color(0x22FFFFFF))
                                .border(1.dp, if (isSelected) CyanNeon else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { onLanguageChange(lang) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lang.displayName.split(" ")[0],
                                color = if (isSelected) CyanNeon else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Continuous Listening Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Continuous Voice Mode", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = "Hands-free automatic listening", color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isHandsFree,
                        onCheckedChange = { onHandsFreeToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyanNeon,
                            checkedTrackColor = Color(0x4400E5FF)
                        )
                    )
                }

                // Accessibility Service Control
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x3300E5FF))
                        .border(1.dp, if (isAccessibilityActive) EmeraldNeon else CyanNeon, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Accessibility, contentDescription = null, tint = CyanNeon)
                            Text(
                                text = "ACCESSIBILITY SERVICE",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = if (isAccessibilityActive) "ONLINE" else "DISABLED",
                                color = if (isAccessibilityActive) EmeraldNeon else CrimsonNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Required for reading screen text, clicking buttons, and home/back navigation.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = { MaxAccessibilityService.openAccessibilitySettings(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_accessibility_settings_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DeepSpaceBlack),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Open Accessibility Settings", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Android Hardware Permissions
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth().testTag("grant_permissions_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = VioletNeon, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Grant Phone, SMS & Mic Permissions")
                }

                // Gemini API Key Input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "GEMINI API KEY (Optional Custom)", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            keySavedMessage = false
                        },
                        placeholder = { Text("Enter Gemini API Key...", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("api_key_field"),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (keySavedMessage) {
                            Text(text = "API Key Saved!", color = EmeraldNeon, fontSize = 12.sp)
                        } else {
                            Spacer(modifier = Modifier.size(1.dp))
                        }
                        Button(
                            onClick = {
                                if (apiKeyInput.isNotBlank()) {
                                    onApiKeySaved(apiKeyInput.trim())
                                    keySavedMessage = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DeepSpaceBlack),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Save Key")
                        }
                    }
                }

                // 🔐 Phantom Nexus Memory Wallet Security Status
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x2200E5FF))
                        .border(1.dp, CyanNeon.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = EmeraldNeon, modifier = Modifier.size(16.dp))
                            Text(
                                text = "PHANTOM NEXUS MEMORY WALLET",
                                color = EmeraldNeon,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Room Database is locked with 256-bit AES-GCM encrypted SQLCipher layer via Android KeyStore. All chats & preferences stay on-device.",
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Emergency Stop
                Button(
                    onClick = {
                        onEmergencyStop()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("emergency_stop_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonNeon, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.StopCircle, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("EMERGENCY STOP (आपातकालीन रोक)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
