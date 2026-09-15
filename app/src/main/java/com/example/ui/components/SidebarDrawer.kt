package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bridge.DeviceSpecs
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.MagentaNeon
import com.example.ui.theme.SpaceNavy
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletNeon

enum class NavigationDestination {
    HOME,
    CONSOLE,
    TASKS,
    AGENTS,
    MEMORY,
    PERMISSIONS,
    FILES,
    BROWSER,
    VISION,
    AUTOMATIONS,
    DEVICES,
    SYSTEM,
    SETTINGS,
    DEVELOPER,
    NEWS,
    PROVIDERS
}

@Composable
fun SidebarDrawerContent(
    currentDestination: NavigationDestination,
    deviceSpecs: DeviceSpecs,
    onDestinationSelected: (NavigationDestination) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(310.dp)
            .background(Color(0xF0080E1B))
            .border(1.dp, GlassBorder, RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Futuristic Emblem
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF1E1045), Color(0xFF0F2644)))
                        )
                        .border(1.2.dp, CyanNeon, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("◆", color = CyanNeon, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(
                        text = "PHANTOM NEXUS",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "AI OS LAYER",
                        color = CyanNeon,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp
                    )
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("close_sidebar_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Menu",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Navigation Items
        val items = listOf(
            Triple(NavigationDestination.HOME, "Home", Icons.Default.Home),
            Triple(NavigationDestination.CONSOLE, "Console", Icons.Default.Terminal),
            Triple(NavigationDestination.TASKS, "Tasks", Icons.Default.Task),
            Triple(NavigationDestination.AGENTS, "Agents", Icons.Default.People),
            Triple(NavigationDestination.MEMORY, "Memory Wallet (Encrypted)", Icons.Default.Bookmark),
            Triple(NavigationDestination.PERMISSIONS, "Hardware Permissions", Icons.Default.Security),
            Triple(NavigationDestination.FILES, "Files", Icons.Default.Folder),
            Triple(NavigationDestination.BROWSER, "Browser", Icons.Default.Language),
            Triple(NavigationDestination.VISION, "Vision", Icons.Default.Visibility),
            Triple(NavigationDestination.AUTOMATIONS, "Automations", Icons.Default.SmartToy),
            Triple(NavigationDestination.DEVICES, "Devices", Icons.Default.Devices),
            Triple(NavigationDestination.SYSTEM, "System", Icons.Default.Computer),
            Triple(NavigationDestination.SETTINGS, "Settings", Icons.Default.Settings),
            Triple(NavigationDestination.DEVELOPER, "Developer", Icons.Default.Code)
        )

        items.forEach { (dest, label, icon) ->
            val isSelected = currentDestination == dest
            DrawerNavItem(
                label = label,
                icon = icon,
                isSelected = isSelected,
                onClick = {
                    onDestinationSelected(dest)
                    onClose()
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SYSTEM OVERVIEW HUD
        Text(
            text = "SYSTEM OVERVIEW",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x990A1122))
                .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    GaugeCircle(label = "CPU", value = "24%", progress = 0.24f, color = CyanNeon)
                    GaugeCircle(
                        label = "RAM",
                        value = "${((deviceSpecs.availableRamMb.toFloat() / (deviceSpecs.totalRamMb.coerceAtLeast(1))) * 100).toInt()}%",
                        progress = 0.48f,
                        color = VioletNeon
                    )
                    GaugeCircle(
                        label = "BAT",
                        value = "${deviceSpecs.batteryPercent}%",
                        progress = deviceSpecs.batteryPercent / 100f,
                        color = if (deviceSpecs.isCharging) EmeraldNeon else CyanNeon
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "OS: ${deviceSpecs.androidVersion}", color = TextSecondary, fontSize = 11.sp)
                    Text(text = "Status: Optimal", color = EmeraldNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Uptime: ${deviceSpecs.uptimeHours}h", color = TextMuted, fontSize = 10.sp)
                    Text(text = "Storage: ${deviceSpecs.storageFreeGb}GB free", color = TextMuted, fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Footer matching reference image
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x220A1628))
                .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF1E2640), Color(0xFF0C1424))))
                        .border(1.dp, CyanNeon.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("◆", color = CyanNeon, fontSize = 14.sp)
                }
                Column {
                    Text(
                        text = "MAX",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(EmeraldNeon)
                        )
                        Text(
                            text = "All Systems Operational",
                            color = EmeraldNeon,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Text("v1.0.0", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun DrawerNavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgModifier = if (isSelected) {
        Modifier
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0x99512DA8), Color(0x3300E5FF))
                ),
                RoundedCornerShape(10.dp)
            )
            .border(1.dp, CyanNeon.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(bgModifier)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) CyanNeon else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = if (isSelected) Color.White else TextPrimary,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun GaugeCircle(
    label: String,
    value: String,
    progress: Float,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = color,
                trackColor = Color(0x22FFFFFF),
                strokeWidth = 3.dp,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
