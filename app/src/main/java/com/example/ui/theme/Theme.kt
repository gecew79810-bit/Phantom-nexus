package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = CyanNeon,
    onPrimary = DeepSpaceBlack,
    secondary = VioletNeon,
    onSecondary = Color.White,
    tertiary = MagentaNeon,
    onTertiary = Color.White,
    background = DeepSpaceBlack,
    onBackground = TextPrimary,
    surface = SpaceNavy,
    onSurface = TextPrimary,
    surfaceVariant = GlassSurface,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder
  )

private val LightColorScheme = DarkColorScheme // MAX is a dedicated dark sci-fi OS interface

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to futuristic dark
  dynamicColor: Boolean = false, // Keep sci-fi brand palette consistent
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = DarkColorScheme, typography = Typography, content = content)
}
