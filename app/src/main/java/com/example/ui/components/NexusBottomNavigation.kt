package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

/**
 * Bottom Navigation Bar:
 * HOME, CHAT, AUTOMATIONS, DEVICES, SETTINGS
 * Designed with equal width distribution, compact labels, and no text wrapping.
 */
@Composable
fun NexusBottomNavigation(
    currentDestination: NavigationDestination,
    onDestinationSelected: (NavigationDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xF5080E1B))
            .border(1.dp, GlassBorder)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NexusBottomNavItem(
            icon = Icons.Default.Home,
            label = "Home",
            isSelected = currentDestination == NavigationDestination.HOME,
            tag = "bottom_nav_home",
            modifier = Modifier.weight(1f),
            onClick = { onDestinationSelected(NavigationDestination.HOME) }
        )
        NexusBottomNavItem(
            icon = Icons.Default.Terminal,
            label = "Chat",
            isSelected = currentDestination == NavigationDestination.CONSOLE,
            tag = "bottom_nav_chat",
            modifier = Modifier.weight(1f),
            onClick = { onDestinationSelected(NavigationDestination.CONSOLE) }
        )
        NexusBottomNavItem(
            icon = Icons.Default.AutoAwesome,
            label = "Automations",
            isSelected = currentDestination == NavigationDestination.AUTOMATIONS,
            tag = "bottom_nav_auto",
            modifier = Modifier.weight(1.15f),
            onClick = { onDestinationSelected(NavigationDestination.AUTOMATIONS) }
        )
        NexusBottomNavItem(
            icon = Icons.Default.Devices,
            label = "Devices",
            isSelected = currentDestination == NavigationDestination.DEVICES,
            tag = "bottom_nav_devices",
            modifier = Modifier.weight(1f),
            onClick = { onDestinationSelected(NavigationDestination.DEVICES) }
        )
        NexusBottomNavItem(
            icon = Icons.Default.Settings,
            label = "Settings",
            isSelected = currentDestination == NavigationDestination.SETTINGS,
            tag = "bottom_nav_settings",
            modifier = Modifier.weight(1f),
            onClick = { onDestinationSelected(NavigationDestination.SETTINGS) }
        )
    }
}

@Composable
fun NexusBottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val contentColor = if (isSelected) CyanNeon else TextMuted
    val bgModifier = if (isSelected) {
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x3300E5FF))
    } else {
        Modifier
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .then(bgModifier)
            .clickable { onClick() }
            .padding(vertical = 6.dp)
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = contentColor,
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
