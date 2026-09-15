package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletNeon

import androidx.compose.ui.window.DialogProperties

@Composable
fun PhantomNexusPatchDialog(
    isUpdateRequired: Boolean = true,
    onResolvePatch: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onManageSystemSettings: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = {
            if (!isUpdateRequired) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isUpdateRequired,
            dismissOnClickOutside = !isUpdateRequired
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF5060C18))
                .border(1.5.dp, if (isUpdateRequired) CyanNeon else GlassBorder, RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header with Pulsing Version Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(CyanNeon, VioletNeon))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = DeepSpaceBlack,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "PHANTOM NEXUS",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "SYSTEM PATCH TARGET v1.3.5",
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    if (!isUpdateRequired) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("patch_dialog_close")
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x33FF1744))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "MANDATORY",
                                color = Color(0xFFFF5252),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                // Patch Verification Status Chip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isUpdateRequired) Color(0x2200E5FF) else Color(0x2200E676))
                        .border(
                            1.dp,
                            if (isUpdateRequired) CyanNeon.copy(alpha = 0.5f) else EmeraldNeon.copy(alpha = 0.5f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isUpdateRequired) CyanNeon else EmeraldNeon)
                    )
                    Text(
                        text = if (isUpdateRequired)
                            "UPDATE CHECKER: PATCH v1.3.5 PENDING RESOLUTION"
                        else
                            "PATCH DETECTED: VERSION 1.3.5 ENGINE VERIFIED",
                        color = if (isUpdateRequired) CyanNeon else EmeraldNeon,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Required Box with Exact Note
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0A1424))
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = CyanNeon,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "PATCH NOTES (VERIFIED SPECIFICATION):",
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Text(
                            text = "VoiceFirst mobile AI assistant for Android version 1.3.5 engine. Interruption-proof real-time payload streaming real-time input. Real-time multimodal screen capture vision and floating over overlay dock. Hands-free voice commander and deep link app launcher. Device status and GPS location lookup battery Wi-Fi OS. Instant hardware control flashlight Wi-Fi Bluetooth hotspot. Smart calendar integration and meeting scheduler. Personal memory wallet save your fact and preference. 100% BYOK (Bring Your Own Key) encrypted local storage. 24/7 background execution with zero daily limits.",
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            lineHeight = 16.5.sp
                        )
                    }
                }

                // AI Engine & Neural Subsystem Architecture
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x22101F38))
                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🧠 SYSTEM SUBSYSTEM INTEGRITY (v1.3.5):",
                        color = CyanNeon,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text("• 100% Offline Ollama Integration (Llama 3 8B, No API Key, Complete Privacy)", color = TextPrimary, fontSize = 10.sp)
                    Text("• Transformer Self-Attention & Sub-Word Tokenizer (Hindi + Hinglish Slang + English)", color = TextPrimary, fontSize = 10.sp)
                    Text("• RLHF & DPO Personality (24/7 Care, Affection & अनस्टॉपेबल नटखटपन)", color = TextPrimary, fontSize = 10.sp)
                    Text("• High-Dimensional Vector Embeddings & KV Cache Context Memory", color = TextPrimary, fontSize = 10.sp)
                    Text("• INT8 Quantized / FP16 Tensor Optimization (<45ms Latency, Zero Lag)", color = TextPrimary, fontSize = 10.sp)
                    Text("• Real-Time Safety Guardrails & Privacy Alignment Layer", color = TextPrimary, fontSize = 10.sp)
                    Text("• Natural Human-Like TTS with Microsoft Edge Swara Neural & Expressive Breath Pauses", color = TextPrimary, fontSize = 10.sp)
                }

                // Core Architecture Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FeatureBadge(
                        icon = Icons.Default.Speed,
                        title = "INT8 / FP16",
                        desc = "Zero Lag",
                        modifier = Modifier.weight(1f)
                    )
                    FeatureBadge(
                        icon = Icons.Default.Lock,
                        title = "100% Offline",
                        desc = "Zero Key",
                        modifier = Modifier.weight(1f)
                    )
                    FeatureBadge(
                        icon = Icons.Default.Security,
                        title = "Guardrails",
                        desc = "Safe & Private",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Action Buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onResolvePatch()
                            if (!isUpdateRequired) onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("apply_patch_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DeepSpaceBlack),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = if (isUpdateRequired)
                                "RESOLVE & APPLY SYSTEM PATCH v1.3.5"
                            else
                                "RE-VERIFY SYSTEM INTEGRITY v1.3.5",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = {
                            onManageSystemSettings()
                            if (!isUpdateRequired) onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manage_system_settings_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x3300E5FF), contentColor = CyanNeon),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "MANAGE SYSTEM SETTINGS (अनुमतियां)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x330E1D34))
            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(14.dp))
            Text(text = title, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(text = desc, color = TextMuted, fontSize = 9.sp)
        }
    }
}
