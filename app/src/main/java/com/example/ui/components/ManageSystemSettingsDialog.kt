package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.ai.MaxOrchestrator
import com.example.service.MaxAccessibilityService
import com.example.service.MaxNotificationBridge
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletNeon

@Composable
fun ManageSystemSettingsDialog(
    context: Context,
    orchestrator: MaxOrchestrator,
    onRequestMicrophone: () -> Unit,
    onRequestPhonePermissions: () -> Unit,
    onDismiss: () -> Unit
) {
    val isAccessibilityActive by MaxAccessibilityService.isConnected.collectAsState()
    val isNotificationConnected by MaxNotificationBridge.isServiceConnected.collectAsState()
    val isAutoReplyEnabled by MaxNotificationBridge.isAutoReplyEnabled.collectAsState()
    val mediaState by orchestrator.mediaManager.mediaState.collectAsState()
    val callState by orchestrator.callManager.callState.collectAsState()

    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val hasPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    val hasCanDrawOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF5060E1C))
                .border(1.5.dp, CyanNeon.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x3300E5FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = CyanNeon,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "MANAGE SYSTEM SETTINGS",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "सिस्टम सेटिंग्स एवं अनुमतियां",
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("manage_settings_close")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                // Description
                Text(
                    text = "हार्डवेयर नियंत्रण, बैकग्राउंड निष्पादन और फ्लोटिंग ओवरले अनुमतियों को यहाँ से सीधे प्रबंधित करें:",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                // 1. Microphone Permission
                PermissionTile(
                    title = "माइक्रोफोन अनुमति (RECORD AUDIO)",
                    subtitle = "Instant low-latency voice recognition",
                    isGranted = hasMic,
                    icon = Icons.Default.Mic,
                    onGrant = onRequestMicrophone,
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )

                // 2. Telecom & Call Screening
                PermissionTile(
                    title = "टेलीकॉम व कॉल स्क्रीनिंग (CALL & TELECOM)",
                    subtitle = "Incoming call detection, TTS Announcer & voice answer",
                    isGranted = hasPhone,
                    icon = Icons.Default.Phone,
                    onGrant = onRequestPhonePermissions,
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )

                // 3. Notification Listener & Auto-Reply
                PermissionTile(
                    title = "नोटिफिकेशन लिस्टनर सेवा (NOTIFICATION LISTENER)",
                    subtitle = "Capture WhatsApp, Telegram, Instagram & SMS for auto-reply",
                    isGranted = isNotificationConnected,
                    icon = Icons.Default.Notifications,
                    onGrant = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )

                // 4. Display Over Other Apps / Pop-up window
                PermissionTile(
                    title = "अन्य ऐप्स के ऊपर दिखाएं (DISPLAY OVER OTHER APPS)",
                    subtitle = "Pop-up window & floating overlay HUD",
                    isGranted = hasCanDrawOverlay,
                    icon = Icons.Default.Layers,
                    onGrant = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            context.startActivity(intent)
                        }
                    },
                    onOpenSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            context.startActivity(intent)
                        }
                    }
                )

                // 5. Accessibility Service
                PermissionTile(
                    title = "एक्सेसिबिलिटी सेवा (ACCESSIBILITY SERVICE)",
                    subtitle = "Screen reader text inspection & gestures",
                    isGranted = isAccessibilityActive,
                    icon = Icons.Default.Accessibility,
                    onGrant = { MaxAccessibilityService.openAccessibilitySettings(context) },
                    onOpenSettings = { MaxAccessibilityService.openAccessibilitySettings(context) }
                )

                // 6. Home Screen & Lock Screen Shortcuts Hub
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF091424))
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Widgets, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                            Text(
                                text = "HOME SCREEN SHORTCUTS & PRIVILEGES:",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        ShortcutBulletItem(title = "Show on lock screen", desc = "Instant wake-up & call announcer on keyguard")
                        ShortcutBulletItem(title = "Display pop-up window while running in background", desc = "Autonomous agent overlay alerts")
                        ShortcutBulletItem(title = "Display pop-up window", desc = "HUD floating dock over games and browsers")
                        ShortcutBulletItem(title = "Permanent notification", desc = "Zero kill policy for 24/7 background runtime")

                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("open_app_details_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x3300E5FF), contentColor = CyanNeon),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Open App Permissions in Android Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 7. System-Wide Media Controller Live Status
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF091424))
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Headset, contentDescription = null, tint = EmeraldNeon, modifier = Modifier.size(16.dp))
                            Text(
                                text = "SYSTEM-WIDE MEDIA CONTROLLER",
                                color = EmeraldNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "${mediaState.title} • ${mediaState.artist}",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { orchestrator.mediaManager.skipToPrevious() }) {
                                Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Prev", tint = TextPrimary)
                            }
                            Button(
                                onClick = { orchestrator.mediaManager.togglePlayPause() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DeepSpaceBlack),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (mediaState.isPlaying) "PAUSE" else "PLAY", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            IconButton(onClick = { orchestrator.mediaManager.skipToNext() }) {
                                Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", tint = TextPrimary)
                            }
                        }
                    }
                }

                // 8. Autonomous Auto-Reply Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x3300E5FF))
                        .border(1.dp, CyanNeon.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Autonomous AI Auto-Reply", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Auto responds to WhatsApp / Telegram in background", color = TextMuted, fontSize = 10.sp)
                    }
                    Switch(
                        checked = isAutoReplyEnabled,
                        onCheckedChange = { MaxNotificationBridge.toggleAutoReply() },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyanNeon, checkedTrackColor = Color(0x4400E5FF))
                    )
                }

                // Dismiss
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DeepSpaceBlack),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("SAVE & CLOSE SETTINGS", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PermissionTile(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    icon: ImageVector,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x2213233D))
            .border(1.dp, if (isGranted) EmeraldNeon.copy(alpha = 0.5f) else CrimsonNeon.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = if (isGranted) EmeraldNeon else CyanNeon, modifier = Modifier.size(16.dp))
                    Text(text = title, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = if (isGranted) "GRANTED" else "REQUIRED",
                    color = if (isGranted) EmeraldNeon else CrimsonNeon,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(text = subtitle, color = TextSecondary, fontSize = 10.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onGrant,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isGranted) Color(0x3300E676) else CyanNeon,
                        contentColor = if (isGranted) EmeraldNeon else DeepSpaceBlack
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(if (isGranted) "Verified Active" else "Grant Now", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF), contentColor = TextPrimary),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Settings", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ShortcutBulletItem(title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(CyanNeon)
        )
        Column {
            Text(text = title, color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(text = desc, color = TextMuted, fontSize = 10.sp)
        }
    }
}
