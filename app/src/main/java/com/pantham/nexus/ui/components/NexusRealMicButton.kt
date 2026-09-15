package com.pantham.nexus.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.SpaceNavy
import com.pantham.nexus.voice.MicState

@Composable
fun NexusRealMicButton(
    state: MicState,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "mic_animation")

    val pulse = transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val isListening = state == MicState.LISTENING || state == MicState.STARTING
    val isProcessing = state == MicState.PROCESSING

    val outerGlowColor = when {
        isListening -> CyanNeon.copy(alpha = 0.5f)
        isProcessing -> ElectricBlue.copy(alpha = 0.4f)
        else -> GlassBorder
    }

    Box(
        modifier = modifier
            .size(86.dp)
            .scale(if (isListening) pulse.value else 1f)
            .semantics {
                contentDescription = if (isListening) "Stop microphone" else "Start microphone"
                role = Role.Button
            }
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = if (isListening) {
                        listOf(CyanNeon, ElectricBlue, SpaceNavy)
                    } else {
                        listOf(SpaceNavy, DeepSpaceBlack)
                    }
                ),
                shape = CircleShape
            )
            .border(
                width = if (isListening) 3.dp else 2.dp,
                color = if (isListening) CyanNeon else outerGlowColor,
                shape = CircleShape
            )
            /*
             * Direct, unintercepted clickable modifier.
             * No parent clickable or overlay blocking touches.
             */
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onMicClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when {
                isListening -> Icons.Default.Mic
                isProcessing -> Icons.Default.Stop
                else -> Icons.Default.MicNone
            },
            contentDescription = if (isListening) "Stop microphone" else "Start microphone",
            tint = if (isListening) DeepSpaceBlack else CyanNeon,
            modifier = Modifier.size(38.dp)
        )
    }
}
