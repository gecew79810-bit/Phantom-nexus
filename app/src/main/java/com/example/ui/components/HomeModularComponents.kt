package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.ActionStatus
import com.example.ai.TaskItem
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletNeon
import com.example.voice.VoiceState

/**
 * Clean responsive Quick Actions grid with no label breaking:
 * SCHEDULE, ANALYZE, RESEARCH, SYSTEM
 */
@Composable
fun QuickActionsSection(
    onScheduleClick: () -> Unit,
    onAnalyzeClick: () -> Unit,
    onResearchClick: () -> Unit,
    onSystemClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickActionButtonItem(
            icon = Icons.Default.CalendarMonth,
            label = "SCHEDULE",
            tag = "quick_action_schedule",
            modifier = Modifier.weight(1f),
            onClick = onScheduleClick
        )
        QuickActionButtonItem(
            icon = Icons.Default.AutoAwesome,
            label = "ANALYZE",
            tag = "quick_action_analyze",
            modifier = Modifier.weight(1f),
            onClick = onAnalyzeClick
        )
        QuickActionButtonItem(
            icon = Icons.Default.Search,
            label = "RESEARCH",
            tag = "quick_action_research",
            modifier = Modifier.weight(1f),
            onClick = onResearchClick
        )
        QuickActionButtonItem(
            icon = Icons.Default.Computer,
            label = "SYSTEM",
            tag = "quick_action_system",
            modifier = Modifier.weight(1f),
            onClick = onSystemClick
        )
    }
}

@Composable
fun QuickActionButtonItem(
    icon: ImageVector,
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .crystallineGlass(
                shape = RoundedCornerShape(10.dp),
                specularGleam = true,
                noiseDensity = 1.1f,
                alphaSubstrate = 0.72f
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = CyanNeon,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Task Timeline Component with futuristic glass styling and genuine empty state:
 * "No recent activity"
 */
@Composable
fun TaskTimelineCard(
    tasks: List<TaskItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    CrystallineGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        specularGleam = true,
        noiseDensity = 1.0f
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTION HISTORY",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (tasks.any { it.status == ActionStatus.RUNNING }) "RUNNING" else "STANDBY",
                    color = if (tasks.any { it.status == ActionStatus.RUNNING }) CyanNeon else TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (tasks.isEmpty()) {
                // Authentic Empty State per spec
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x33000000))
                        .border(0.5.dp, GlassBorder, RoundedCornerShape(8.dp))
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent activity",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tasks.take(4).forEach { task ->
                        ActionHistoryItemRow(task = task)
                    }
                }
            }
        }
    }
}

@Composable
fun ActionHistoryItemRow(task: TaskItem) {
    val (statusLabel, statusColor, statusBg) = when (task.status) {
        ActionStatus.RUNNING -> Triple("RUNNING", CyanNeon, Color(0x3300E5FF))
        ActionStatus.WAITING_FOR_PERMISSION -> Triple("WAITING FOR PERMISSION", Color(0xFFFF9800), Color(0x33FF9800))
        ActionStatus.WAITING_FOR_CONFIRMATION -> Triple("WAITING FOR CONFIRMATION", Color(0xFFFFD54F), Color(0x33FFD54F))
        ActionStatus.SUCCESS -> Triple("SUCCESS", EmeraldNeon, Color(0x3300E676))
        ActionStatus.FAILED -> Triple("FAILED", CrimsonNeon, Color(0x33FF1744))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x22000000))
            .border(0.5.dp, GlassBorder, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.title,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusBg)
                        .border(0.5.dp, statusColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ID: ${task.id}",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = task.time,
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (task.details.isNotBlank()) {
                Text(
                    text = task.details,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 2
                )
            }
        }
    }
}

/**
 * Quick Console Component with authentic empty state:
 * "MAX: Ready when you are."
 */
@Composable
fun QuickConsoleCard(
    languageLabel: String,
    modifier: Modifier = Modifier
) {
    CrystallineGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        specularGleam = true,
        noiseDensity = 1.0f
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "QUICK CONSOLE",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = languageLabel,
                    color = CyanNeon,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Authentic Empty State per specification
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x33000000))
                    .border(1.dp, Color(0x2200E5FF), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("MAX", color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("• AI OS LAYER", color = TextMuted, fontSize = 10.sp)
                    }
                    Text(
                        text = "Ready when you are.",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

/**
 * Futuristic Voice Panel with glowing circular core, waveforms, and visual states
 */
@Composable
fun FuturisticVoicePanel(
    voiceState: VoiceState,
    rmsLevel: Float,
    liveTranscript: String,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
    isWakeListening: Boolean = true,
    isWakeTriggered: Boolean = false,
    onToggleWakeListening: (() -> Unit)? = null
) {
    val isVoiceActive = voiceState == VoiceState.LISTENING || voiceState == VoiceState.SPEAKING
    CrystallineGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        glowAccent = CyanNeon,
        specularGleam = true,
        noiseDensity = 1.0f,
        isActive = isVoiceActive
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isWakeListening) CyanNeon else TextMuted)
                    )
                    Text(
                        text = "VOICE ACTIVE",
                        color = CyanNeon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = voiceState.name,
                    color = if (voiceState == VoiceState.LISTENING) CyanNeon else TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Live Transcript if available
            if (liveTranscript.isNotBlank() && voiceState == VoiceState.LISTENING) {
                Text(
                    text = "\"$liveTranscript\"",
                    color = CyanNeon,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }

            // Symmetrical Waveform Flanking MAX Voice Core (matching reference image)
            val isListening = voiceState == VoiceState.LISTENING
            val isSpeaking = voiceState == VoiceState.SPEAKING
            val isVoiceActive = isListening || isSpeaking

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Audio Waveform
                AudioWaveformSide(
                    isActive = isVoiceActive,
                    rmsLevel = rmsLevel,
                    isLeft = true,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.size(8.dp))

                // Center Glowing Circular MAX Voice Core
                Box(
                    modifier = Modifier.size(70.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer Pulsing Atmospheric Ring
                    Box(
                        modifier = Modifier
                            .size(if (isVoiceActive) 70.dp else 64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        if (isListening) CyanNeon.copy(alpha = 0.35f) else VioletNeon.copy(alpha = 0.25f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                if (isListening) CyanNeon.copy(alpha = 0.7f) else VioletNeon.copy(alpha = 0.4f),
                                CircleShape
                            )
                    )

                    // Core Mic Button
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = if (isListening)
                                        listOf(CyanNeon, ElectricBlue, VioletNeon)
                                    else
                                        listOf(Color(0xFF162544), Color(0xFF091222))
                                )
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (isListening) Color.White else CyanNeon,
                                shape = CircleShape
                            )
                            .clickable { onMicClick() }
                            .testTag("main_mic_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop Listening" else "Tap to Speak",
                            tint = if (isListening) DeepSpaceBlack else CyanNeon,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.size(8.dp))

                // Right Audio Waveform
                AudioWaveformSide(
                    isActive = isVoiceActive,
                    rmsLevel = rmsLevel,
                    isLeft = false,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = when (voiceState) {
                    VoiceState.LISTENING -> "Listening to command..."
                    VoiceState.THINKING -> "Thinking..."
                    VoiceState.SPEAKING -> "Speaking..."
                    else -> "Say \"Wake Panthom\" or tap mic"
                },
                color = if (isListening) CyanNeon else TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            // Sleek Wake Word Pill Indicator
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isWakeListening) CyanNeon.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f))
                    .border(
                        1.dp,
                        if (isWakeListening) CyanNeon.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(enabled = onToggleWakeListening != null) { onToggleWakeListening?.invoke() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isWakeListening) CyanNeon else Color.Gray)
                )
                Text(
                    text = if (isWakeListening) "WAKE PHRASE: \"WAKE PANTHOM\" READY" else "WAKE WORD STANDBY",
                    color = if (isWakeListening) CyanNeon else TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}
