package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenSearchDesktop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.InteractiveElement
import com.example.service.MaxAccessibilityService
import com.example.service.ScreenInspectionResult
import com.example.service.UiEventRecord
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletNeon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UI Monitor View:
 * Displays real-time UI events, current foreground app/activity,
 * intercepted notifications, and accessibility service connection status.
 */
@Composable
fun UiEventMonitorView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isConnected by MaxAccessibilityService.isConnected.collectAsState()
    val currentPkg by MaxAccessibilityService.currentPackage.collectAsState()
    val currentAct by MaxAccessibilityService.currentActivity.collectAsState()
    val events by MaxAccessibilityService.recentEvents.collectAsState()
    val lastNotif by MaxAccessibilityService.lastNotification.collectAsState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status & Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GlassSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) EmeraldNeon else CrimsonNeon)
                        )
                        Text(
                            text = if (isConnected) "ACCESSIBILITY MONITOR ONLINE" else "SERVICE DISABLED",
                            color = if (isConnected) EmeraldNeon else CrimsonNeon,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }

                    Button(
                        onClick = { MaxAccessibilityService.openAccessibilitySettings(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) Color(0x2200E5FF) else CyanNeon,
                            contentColor = if (isConnected) CyanNeon else DeepSpaceBlack
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("monitor_open_settings_btn")
                    ) {
                        Text(
                            text = if (isConnected) "Settings" else "Enable",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Foreground App Tracking Info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x33000000))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Foreground App:",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = if (currentPkg.isNotBlank()) currentPkg else "None detected",
                        color = CyanNeon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                if (currentAct.isNotBlank()) {
                    Text(
                        text = "Activity: ${currentAct.substringAfterLast(".")}",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Intercepted Notification Banner
        if (lastNotif != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0x33512DA8)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VioletNeon.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notification",
                        tint = VioletNeon,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "INTERCEPTED NOTIFICATION",
                            color = VioletNeon,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${lastNotif?.title}: ${lastNotif?.text}",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                        Text(
                            text = "From: ${lastNotif?.packageName}",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // Real-Time Event Log List
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GlassSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE UI EVENT STREAM (${events.size})",
                        color = CyanNeon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    IconButton(
                        onClick = { MaxAccessibilityService.clearEventLogs() },
                        modifier = Modifier.size(28.dp).testTag("clear_events_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Logs",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (events.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No UI events captured yet.\nInteract with apps or switch screens to see live telemetry.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        events.takeLast(10).reversed().forEach { event ->
                            UiEventCard(event = event)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UiEventCard(event: UiEventRecord) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = timeFormat.format(Date(event.timestamp))

    val badgeColor = when {
        event.eventTypeName.contains("CLICK") -> CyanNeon
        event.eventTypeName.contains("NOTIFICATION") -> VioletNeon
        event.eventTypeName.contains("TEXT") -> EmeraldNeon
        event.eventTypeName.contains("WINDOW") -> ElectricBlue
        else -> TextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x330A1122))
            .border(0.5.dp, GlassBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor.copy(alpha = 0.2f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = event.eventTypeName,
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = event.packageName.substringAfterLast("."),
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            if (event.text.isNotBlank() || event.contentDescription.isNotBlank()) {
                Text(
                    text = if (event.text.isNotBlank()) event.text else event.contentDescription,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Text(
            text = timeStr,
            color = TextMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Gestures & Cross-App Interaction Control Panel:
 * Allows executing precision gestures (scroll, swipe, tap, double tap) and
 * inspecting active screen elements to click or input text into other apps.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GestureControlPanelView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var targetTextToClick by remember { mutableStateOf("") }
    var textToType by remember { mutableStateOf("") }
    var inspectionResult by remember { mutableStateOf<ScreenInspectionResult?>(null) }
    var isInspecting by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Quick Gesture Controller Grid
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GlassSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "GESTURE CONTROLLER (DISPATCH GESTURE)",
                    color = CyanNeon,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "Dispatches hardware-accurate gesture strokes across apps via Accessibility.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )

                // Gestures Grid
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GestureChip(
                        icon = Icons.Default.ArrowDownward,
                        label = "Scroll Down",
                        tag = "gesture_scroll_down",
                        onClick = {
                            scope.launch {
                                val ok = MaxAccessibilityService.scrollDown()
                                Toast.makeText(context, if (ok) "Scrolled Down" else "Service not enabled", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    GestureChip(
                        icon = Icons.Default.ArrowUpward,
                        label = "Scroll Up",
                        tag = "gesture_scroll_up",
                        onClick = {
                            scope.launch {
                                val ok = MaxAccessibilityService.scrollUp()
                                Toast.makeText(context, if (ok) "Scrolled Up" else "Service not enabled", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    GestureChip(
                        icon = Icons.Default.TouchApp,
                        label = "Tap Center",
                        tag = "gesture_tap_center",
                        onClick = {
                            scope.launch {
                                val metrics = context.resources.displayMetrics
                                val ok = MaxAccessibilityService.tap(metrics.widthPixels / 2f, metrics.heightPixels / 2f)
                                Toast.makeText(context, if (ok) "Tapped Center" else "Service not enabled", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    GestureChip(
                        icon = Icons.Default.AdsClick,
                        label = "Double Tap",
                        tag = "gesture_double_tap",
                        onClick = {
                            scope.launch {
                                val metrics = context.resources.displayMetrics
                                val ok = MaxAccessibilityService.doubleTap(metrics.widthPixels / 2f, metrics.heightPixels / 2f)
                                Toast.makeText(context, if (ok) "Double Tapped" else "Service not enabled", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    GestureChip(
                        icon = Icons.Default.Swipe,
                        label = "Long Press",
                        tag = "gesture_long_press",
                        onClick = {
                            scope.launch {
                                val metrics = context.resources.displayMetrics
                                val ok = MaxAccessibilityService.longPress(metrics.widthPixels / 2f, metrics.heightPixels / 2f)
                                Toast.makeText(context, if (ok) "Long Pressed" else "Service not enabled", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Global System Actions Row
                Text(
                    text = "GLOBAL SYSTEM ACTIONS",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GestureChip(
                        icon = Icons.Default.Home,
                        label = "Home",
                        tag = "action_home",
                        onClick = { MaxAccessibilityService.performHome() }
                    )
                    GestureChip(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "Back",
                        tag = "action_back",
                        onClick = { MaxAccessibilityService.performBack() }
                    )
                    GestureChip(
                        icon = Icons.Default.ViewCarousel,
                        label = "Recents",
                        tag = "action_recents",
                        onClick = { MaxAccessibilityService.performRecents() }
                    )
                    GestureChip(
                        icon = Icons.Default.Notifications,
                        label = "Notifications",
                        tag = "action_notifs",
                        onClick = { MaxAccessibilityService.performNotifications() }
                    )
                    GestureChip(
                        icon = Icons.Default.SettingsSuggest,
                        label = "Quick Settings",
                        tag = "action_quick_settings",
                        onClick = { MaxAccessibilityService.performQuickSettings() }
                    )
                    GestureChip(
                        icon = Icons.Default.FitScreen,
                        label = "Screenshot",
                        tag = "action_screenshot",
                        onClick = {
                            scope.launch {
                                val ok = MaxAccessibilityService.takeScreenshotAsync()
                                Toast.makeText(context, if (ok) "Screenshot Captured" else "Screenshot action failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        // Interactive Node Clicker & Typer
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GlassSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "CROSS-APP UI ELEMENT INTERACTION",
                    color = CyanNeon,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )

                // Click by text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = targetTextToClick,
                        onValueChange = { targetTextToClick = it },
                        placeholder = { Text("Button / text to click...", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f).testTag("click_text_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Button(
                        onClick = {
                            if (targetTextToClick.isNotBlank()) {
                                scope.launch {
                                    val ok = MaxAccessibilityService.clickNodeWithText(targetTextToClick)
                                    Toast.makeText(
                                        context,
                                        if (ok) "Clicked '$targetTextToClick'" else "Not found on screen",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DeepSpaceBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("execute_click_btn")
                    ) {
                        Text("Click", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // Type text into active field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textToType,
                        onValueChange = { textToType = it },
                        placeholder = { Text("Text to type into input...", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f).testTag("type_text_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VioletNeon,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Button(
                        onClick = {
                            if (textToType.isNotBlank()) {
                                scope.launch {
                                    val ok = MaxAccessibilityService.inputText(textToType)
                                    Toast.makeText(
                                        context,
                                        if (ok) "Text entered" else "No editable focus found",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VioletNeon, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("execute_type_btn")
                    ) {
                        Text("Type", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Screen Hierarchy Inspector
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GlassSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
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
                        text = "SCREEN HIERARCHY SCANNER",
                        color = CyanNeon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Button(
                        onClick = {
                            isInspecting = true
                            inspectionResult = MaxAccessibilityService.inspectScreenHierarchy()
                            isInspecting = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x3300E5FF), contentColor = CyanNeon),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("scan_screen_hierarchy_btn")
                    ) {
                        Icon(imageVector = Icons.Default.ScreenSearchDesktop, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scan Screen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (inspectionResult != null) {
                    val res = inspectionResult!!
                    Text(
                        text = "Package: ${res.packageName} (${res.interactiveElements.size} interactive nodes)",
                        color = EmeraldNeon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        res.interactiveElements.take(8).forEach { elem ->
                            InteractiveNodeRow(
                                element = elem,
                                onNodeClick = {
                                    scope.launch {
                                        val ok = MaxAccessibilityService.tap(elem.centerX, elem.centerY)
                                        Toast.makeText(
                                            context,
                                            if (ok) "Tapped (${elem.centerX.toInt()}, ${elem.centerY.toInt()})" else "Failed",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Tap 'Scan Screen' to inspect all interactable buttons, text fields, and list views on the active screen.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun GestureChip(
    icon: ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x2200E5FF))
            .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = CyanNeon, modifier = Modifier.size(15.dp))
        Text(text = label, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun InteractiveNodeRow(
    element: InteractiveElement,
    onNodeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x33000000))
            .border(0.5.dp, GlassBorder, RoundedCornerShape(6.dp))
            .clickable { onNodeClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = element.displayName,
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = "Center: (${element.centerX.toInt()}, ${element.centerY.toInt()}) • ${element.className.substringAfterLast(".")}",
                color = TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x3300E676))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("TAP", color = EmeraldNeon, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}
