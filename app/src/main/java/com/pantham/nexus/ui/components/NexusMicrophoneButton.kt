package com.pantham.nexus.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pantham.nexus.voice.NexusVoiceState

/**
 * High-performance, tactile Cyberpunk Central Microphone FAB for Pantham Nexus.
 * Ensures unambiguous touch targeting, accessibility descriptions, and smooth state transitions.
 */
@Composable
fun NexusMicrophoneButton(
    state: NexusVoiceState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Int = 76
) {
    val scale by animateFloatAsState(
        targetValue = when (state) {
            NexusVoiceState.LISTENING -> 1.15f
            NexusVoiceState.PROCESSING -> 1.06f
            NexusVoiceState.SPEAKING -> 1.08f
            NexusVoiceState.ERROR -> 0.96f
            else -> 1.0f
        },
        animationSpec = tween(durationMillis = 260),
        label = "nexus_mic_scale"
    )

    val description = when (state) {
        NexusVoiceState.LISTENING -> "Stop listening"
        NexusVoiceState.PROCESSING -> "Processing voice"
        NexusVoiceState.SPEAKING -> "Assistant speaking"
        NexusVoiceState.ERROR -> "Microphone error"
        NexusVoiceState.IDLE -> "Microphone"
    }

    val backgroundColor = when (state) {
        NexusVoiceState.LISTENING -> Brush.radialGradient(
            colors = listOf(Color(0xFFFF3366), Color(0xFF990033))
        )
        NexusVoiceState.PROCESSING -> Brush.radialGradient(
            colors = listOf(Color(0xFF00FFCC), Color(0xFF006655))
        )
        NexusVoiceState.SPEAKING -> Brush.radialGradient(
            colors = listOf(Color(0xFF8844FF), Color(0xFF330088))
        )
        NexusVoiceState.ERROR -> Brush.radialGradient(
            colors = listOf(Color(0xFFFF5555), Color(0xFF660000))
        )
        NexusVoiceState.IDLE -> Brush.radialGradient(
            colors = listOf(Color(0xFF00FFCC), Color(0xFF0D2825))
        )
    }

    val iconColor = when (state) {
        NexusVoiceState.LISTENING -> Color.White
        NexusVoiceState.IDLE -> Color(0xFF0A1015)
        else -> Color.White
    }

    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(brush = backgroundColor)
            .testTag("central_voice_fab")
            .semantics {
                this.contentDescription = description
            }
            .clickable {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (state == NexusVoiceState.LISTENING) {
                Icons.Default.MicOff
            } else {
                Icons.Default.Mic
            },
            contentDescription = description,
            tint = iconColor,
            modifier = Modifier.size(34.dp)
        )
    }
}
