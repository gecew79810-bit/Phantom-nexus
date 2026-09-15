package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MagentaNeon
import com.example.ui.theme.VioletNeon
import kotlin.math.sin

@Composable
fun AudioWaveformVisualizer(
    isActive: Boolean,
    rmsLevel: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 36
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
    ) {
        val width = size.width
        val height = size.height
        val barWidth = (width / barCount) * 0.55f
        val spacing = (width / barCount)

        val gradient = Brush.verticalGradient(
            colors = listOf(CyanNeon, ElectricBlue, VioletNeon)
        )

        for (i in 0 until barCount) {
            val normalizedIdx = i.toFloat() / barCount
            val wave = sin((normalizedIdx * 8f) + phase).coerceAtLeast(-1f)

            val baseHeight = if (isActive) {
                val rmsFactor = (rmsLevel.coerceIn(0f, 15f) / 15f)
                val dynamicHeight = (height * 0.25f) + (height * 0.65f * kotlin.math.abs(wave) * (0.4f + 0.6f * rmsFactor))
                dynamicHeight.coerceIn(4.dp.toPx(), height)
            } else {
                // Subtle idle flat pulse
                (height * 0.15f) + (height * 0.1f * kotlin.math.abs(wave))
            }

            val x = i * spacing + (spacing - barWidth) / 2f
            val y = (height - baseHeight) / 2f

            drawRoundRect(
                brush = if (isActive) gradient else Brush.linearGradient(listOf(Color(0x5500E5FF), Color(0x337C4DFF))),
                topLeft = Offset(x, y),
                size = Size(barWidth, baseHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

/**
 * Compact symmetrical side audio waveform for flanking the MAX Voice Core
 */
@Composable
fun AudioWaveformSide(
    isActive: Boolean,
    rmsLevel: Float,
    isLeft: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 12
) {
    val infiniteTransition = rememberInfiniteTransition(label = "side_waveform_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "side_phase"
    )

    Canvas(
        modifier = modifier
            .height(32.dp)
    ) {
        val width = size.width
        val height = size.height
        val barWidth = (width / barCount) * 0.48f
        val spacing = width / barCount

        val gradient = Brush.verticalGradient(
            colors = listOf(CyanNeon, ElectricBlue, VioletNeon)
        )

        for (i in 0 until barCount) {
            val factor = if (isLeft) (i.toFloat() / barCount) else ((barCount - 1 - i).toFloat() / barCount)
            val wave = sin((factor * 6f) + phase)

            val baseHeight = if (isActive) {
                val rmsFactor = (rmsLevel.coerceIn(0f, 15f) / 15f)
                val h = (height * 0.2f) + (height * 0.75f * (0.3f + 0.7f * factor) * kotlin.math.abs(wave) * (0.5f + 0.5f * rmsFactor))
                h.coerceIn(3.dp.toPx(), height)
            } else {
                (height * 0.12f) + (height * 0.18f * factor * kotlin.math.abs(wave))
            }

            val x = i * spacing + (spacing - barWidth) / 2f
            val y = (height - baseHeight) / 2f

            drawRoundRect(
                brush = if (isActive) gradient else Brush.linearGradient(listOf(CyanNeon.copy(alpha = 0.5f), VioletNeon.copy(alpha = 0.3f))),
                topLeft = Offset(x, y),
                size = Size(barWidth, baseHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
