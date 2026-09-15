package com.example.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Design System Specifications for Phantom Nexus AI OS
 */
object NexusDesign {
    // Spacing
    val spaceXs = 4.dp
    val spaceSm = 8.dp
    val spaceMd = 12.dp
    val spaceLg = 16.dp
    val spaceXl = 20.dp
    val space2Xl = 24.dp
    val space3Xl = 32.dp

    // Corner Radius
    val radiusSm = RoundedCornerShape(8.dp)
    val radiusMd = RoundedCornerShape(12.dp)
    val radiusLg = RoundedCornerShape(16.dp)
    val radiusXl = RoundedCornerShape(20.dp)
    val radiusFull = RoundedCornerShape(999.dp)

    // Borders
    val borderThin = BorderStroke(1.dp, GlassBorder)
    val borderSubtle = BorderStroke(0.5.dp, GlassBorder)
    val borderActive = BorderStroke(1.2.dp, CyanNeon)
    val borderViolet = BorderStroke(1.dp, GlassBorderViolet)

    // Glass gradients
    val glassCardGradient = Brush.verticalGradient(
        listOf(
            Color(0xEE0D162B),
            Color(0xF0070D1A)
        )
    )

    val coreSphereGradient = Brush.radialGradient(
        listOf(
            Color(0xFF1E3A5F),
            Color(0xFF0F2038),
            Color(0xFF070E1C),
            Color(0xFF03070E)
        )
    )

    val neonCyanVioletGradient = Brush.linearGradient(
        listOf(CyanNeon, VioletNeon)
    )
}
