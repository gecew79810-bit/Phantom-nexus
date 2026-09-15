package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.permission.CentralPermissionsManager
import com.example.permission.HardwarePermissionType
import com.example.permission.PermissionStatus
import com.example.ui.theme.AmberNeon
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletNeon

/**
 * StartupPermissionsDialog:
 * Displays critical missing hardware permissions on app startup with direct deep-links
 * into Android system settings for each specific item.
 */
@Composable
fun StartupPermissionsDialog(
    context: Context,
    onRequestMicrophoneRuntime: () -> Unit,
    onDismiss: () -> Unit
) {
    // Current permission statuses with manual refresh support
    var statuses by remember { mutableStateOf(CentralPermissionsManager.getPermissionStatuses(context)) }
    val missingCoreCount = statuses.count { it.isCritical && !it.isGranted }

    val refreshStatuses = {
        statuses = CentralPermissionsManager.getPermissionStatuses(context)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF5060D1A))
                .border(1.5.dp, if (missingCoreCount > 0) AmberNeon else CyanNeon, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (missingCoreCount > 0) Color(0x33FFB300) else Color(0x3300E5FF)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (missingCoreCount > 0) Icons.Default.Warning else Icons.Default.Security,
                                contentDescription = null,
                                tint = if (missingCoreCount > 0) AmberNeon else CyanNeon,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "CENTRAL PERMISSIONS MANAGER",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (missingCoreCount > 0) {
                                    "$missingCoreCount Core Permissions Missing"
                                } else {
                                    "All Core Permissions Granted"
                                },
                                color = if (missingCoreCount > 0) AmberNeon else EmeraldNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = refreshStatuses,
                            modifier = Modifier.size(32.dp).testTag("refresh_permissions_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = CyanNeon,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp).testTag("close_permissions_dialog_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Subtitle Info
                Text(
                    text = "Phantom Nexus requires hardware access to execute voice processing, screen reading, and floating overlays. Deep-link directly to Android settings below:",
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )

                // Permission items
                statuses.forEach { item ->
                    PermissionCard(
                        status = item,
                        onDeepLink = {
                            when (item.type) {
                                HardwarePermissionType.MICROPHONE -> {
                                    onRequestMicrophoneRuntime()
                                    // Also open details if already rejected or permanently denied
                                    CentralPermissionsManager.openMicrophoneSettings(context)
                                }
                                HardwarePermissionType.ACCESSIBILITY -> {
                                    CentralPermissionsManager.openAccessibilitySettings(context)
                                }
                                HardwarePermissionType.OVERLAY -> {
                                    CentralPermissionsManager.openOverlaySettings(context)
                                }
                                HardwarePermissionType.NOTIFICATIONS -> {
                                    CentralPermissionsManager.openNotificationSettings(context)
                                }
                                HardwarePermissionType.PHONE_TELECOM,
                                HardwarePermissionType.CALENDAR,
                                HardwarePermissionType.CAMERA,
                                HardwarePermissionType.CONTACTS,
                                HardwarePermissionType.SMS,
                                HardwarePermissionType.LOCATION -> {
                                    CentralPermissionsManager.openAppDetailsSettings(context)
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = refreshStatuses,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("recheck_permissions_btn"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon.copy(alpha = 0.6f))
                    ) {
                        Text("Re-check", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("continue_to_os_btn"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (missingCoreCount > 0) CyanNeon else EmeraldNeon,
                            contentColor = DeepSpaceBlack
                        )
                    ) {
                        Text(
                            text = if (missingCoreCount > 0) "Dismiss" else "Enter MAX OS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual Permission Tile with status indicator & settings deep-link.
 */
@Composable
private fun PermissionCard(
    status: PermissionStatus,
    onDeepLink: () -> Unit
) {
    val icon: ImageVector = when (status.type) {
        HardwarePermissionType.MICROPHONE -> Icons.Default.Mic
        HardwarePermissionType.ACCESSIBILITY -> Icons.Default.Accessibility
        HardwarePermissionType.OVERLAY -> Icons.Default.Layers
        HardwarePermissionType.NOTIFICATIONS -> Icons.Default.Notifications
        HardwarePermissionType.PHONE_TELECOM -> Icons.Default.Phone
        HardwarePermissionType.CALENDAR -> Icons.Default.CalendarMonth
        HardwarePermissionType.CAMERA -> Icons.Default.CameraAlt
        HardwarePermissionType.CONTACTS -> Icons.Default.Contacts
        HardwarePermissionType.SMS -> Icons.Default.Message
        HardwarePermissionType.LOCATION -> Icons.Default.LocationOn
    }

    val badgeColor = if (status.isGranted) EmeraldNeon else if (status.isCritical) CrimsonNeon else AmberNeon
    val borderColor = if (status.isGranted) GlassBorder else if (status.isCritical) CrimsonNeon.copy(alpha = 0.4f) else AmberNeon.copy(alpha = 0.3f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GlassSurface)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x2200E5FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = status.title,
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (status.isCritical && !status.isGranted) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x33FF1744))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "CRITICAL",
                                        color = CrimsonNeon,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                        Text(
                            text = status.description,
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(badgeColor)
                    )
                    Text(
                        text = if (status.isGranted) "GRANTED" else "MISSING",
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Deep-link CTA button if not granted
            if (!status.isGranted) {
                Button(
                    onClick = onDeepLink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .testTag("deeplink_${status.type.name.lowercase()}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (status.isCritical) CyanNeon else VioletNeon,
                        contentColor = DeepSpaceBlack
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Open System Settings",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}
