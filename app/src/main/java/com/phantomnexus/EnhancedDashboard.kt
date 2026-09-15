package com.phantomnexus

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI

// ═══════════════════════════════════════════════
// 🎨 ENHANCED CYBER COLOR SYSTEM
// ═══════════════════════════════════════════════
object EnhancedNexusColors {
    val neonCyan = Color(0xFF00F2FE)
    val neonPurple = Color(0xFF9D4EDD)
    val neonPink = Color(0xFFF5576C)
    val neonGreen = Color(0xFF39FF88)
    val neonAmber = Color(0xFFFFB800)
    val neonBlue = Color(0xFF4F8FF0)

    val safeGradient = listOf(Color(0xFF39FF88), Color(0xFF00F2FE))
    val warnGradient = listOf(Color(0xFFFFB800), Color(0xFFF5576C))
    val dangerGradient = listOf(Color(0xFFF5576C), Color(0xFF9D4EDD))
    val primaryGradient = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
    val neonGradient = listOf(Color(0xFF00F2FE), Color(0xFF9D4EDD))

    val bgDeep = Color(0xFF05070D)
    val bgSurface = Color(0xFF10141F)
    val cardBg = Color(0xFF151A28)
    val cardBorder = Color(0xFF232B40)
    val textPrimary = Color(0xFFEAF0FF)
    val textSecondary = Color(0xFF7D8AA8)
    val accentGlow = Color(0xFF00F2FE).copy(alpha = 0.1f)
}

// ═══════════════════════════════════════════════
// 📊 ENHANCED DATA MODELS
// ═══════════════════════════════════════════════
data class EnhancedDashboardState(
    val safetyScore: Int = 92,
    val threatsBlocked: Int = 247,
    val appsScanned: Int = 156,
    val ghostModeActive: Boolean = true,
    val autoScanEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val lastScanMinutesAgo: Int = 2,
    val vpnActive: Boolean = true,
    val encryptionLevel: String = "Military Grade",
    val cpuUsage: Float = 0.23f,
    val batteryUsage: Float = 0.15f
)

data class ThreatLevel(
    val name: String,
    val count: Int,
    val icon: ImageVector,
    val color: Color,
    val percentage: Float
)

// ═══════════════════════════════════════════════
// ⚡ GLITCH & GLOW EFFECTS
// ═══════════════════════════════════════════════
@Composable
fun GlitchText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    modifier: Modifier = Modifier
) {
    val glitch by rememberInfiniteTransition(label = "glitch").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Restart),
        label = "glitch_value"
    )

    val offsetX = if (glitch > 0.98f) 2.dp else 0.dp
    val offsetY = if (glitch > 0.97f) (-2).dp else 0.dp

    Box(modifier = modifier) {
        Text(text, fontSize = fontSize, color = color.copy(alpha = 0.3f), modifier = Modifier.offset(offsetX, offsetY))
        Text(text, fontSize = fontSize, color = color.copy(alpha = 0.5f), modifier = Modifier.offset((-offsetX), offsetY))
        Text(text, fontSize = fontSize, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun NeonGlowBox(
    modifier: Modifier = Modifier,
    glowColor: Color = EnhancedNexusColors.neonCyan,
    content: @Composable () -> Unit
) {
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "glow_value"
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp + (4.dp * glow),
                shape = RoundedCornerShape(16.dp),
                spotColor = glowColor.copy(alpha = 0.3f * glow),
                clip = false
            )
    ) {
        content()
    }
}

// ═══════════════════════════════════════════════
// 🎯 ADVANCED CIRCULAR PROGRESS WITH SEGMENTS
// ═══════════════════════════════════════════════
@Composable
fun AdvancedCircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    segments: Int = 8,
    primaryColor: Color = EnhancedNexusColors.neonCyan,
    backgroundColor: Color = EnhancedNexusColors.cardBorder
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "advanced_progress"
    )

    Canvas(modifier = modifier.size(120.dp)) {
        val stroke = Stroke(width = 6f, cap = StrokeCap.Round)
        val centerSize = size / 2f

        // Background ring
        drawArc(
            color = backgroundColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = stroke
        )

        // Animated progress arc
        drawArc(
            brush = Brush.sweepGradient(
                listOf(primaryColor, primaryColor.copy(alpha = 0.5f)),
                center = centerSize
            ),
            startAngle = -90f,
            sweepAngle = 360f * animatedProgress,
            useCenter = false,
            style = stroke
        )

        // Segments
        repeat(segments) { i ->
            val angle = (360f / segments) * i
            val radians = Math.toRadians(angle.toDouble()).toFloat()
            val radius = size.width / 2f - 15f
            val x = centerSize.x + radius * cos(radians)
            val y = centerSize.y + radius * sin(radians)

            drawCircle(
                color = if (animatedProgress > (i.toFloat() / segments)) primaryColor else backgroundColor,
                radius = 3f,
                center = Offset(x, y)
            )
        }
    }
}

// ═══════════════════════════════════════════════
// 🛡️ REAL-TIME THREAT RADAR
// ═══════════════════════════════════════════════
@Composable
fun ThreatRadar(modifier: Modifier = Modifier) {
    val rotation by rememberInfiniteTransition(label = "radar").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "radar_rotation"
    )

    Canvas(modifier = modifier.size(200.dp).rotate(rotation)) {
        val center = size / 2f
        val maxRadius = size.minDimension / 2f

        // Circles
        for (i in 1..3) {
            drawCircle(
                color = EnhancedNexusColors.neonCyan.copy(alpha = 0.15f),
                radius = maxRadius * (i / 3f),
                center = center,
                style = Stroke(1f)
            )
        }

        // Cross
        drawLine(
            color = EnhancedNexusColors.neonCyan.copy(alpha = 0.3f),
            start = Offset(center.x, 0f),
            end = Offset(center.x, size.height),
            strokeWidth = 1f
        )
        drawLine(
            color = EnhancedNexusColors.neonCyan.copy(alpha = 0.3f),
            start = Offset(0f, center.y),
            end = Offset(size.width, center.y),
            strokeWidth = 1f
        )

        // Sweep line
        val sweepRadius = maxRadius * 0.9f
        val sweepRadians = (rotation * PI / 180f).toFloat()
        drawLine(
            color = EnhancedNexusColors.neonCyan,
            start = center,
            end = Offset(
                center.x + sweepRadius * cos(sweepRadians),
                center.y + sweepRadius * sin(sweepRadians)
            ),
            strokeWidth = 2f
        )

        // Threat dots
        repeat(3) {
            val threatAngle = (rotation + 90f * it) % 360f
            val threatRadians = (threatAngle * PI / 180f).toFloat()
            val threatRadius = maxRadius * 0.6f
            drawCircle(
                color = EnhancedNexusColors.neonPink,
                radius = 4f,
                center = Offset(
                    center.x + threatRadius * cos(threatRadians),
                    center.y + threatRadius * sin(threatRadians)
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════
// 🚀 ENHANCED MAIN DASHBOARD
// ═══════════════════════════════════════════════
@Composable
fun EnhancedPhantomNexusDashboard() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf(EnhancedDashboardState()) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = EnhancedNexusColors.bgDeep
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            EnhancedHeader(state = state)
            Spacer(modifier = Modifier.height(24.dp))
            DashboardTabs(selectedTab) { selectedTab = it }
            Spacer(modifier = Modifier.height(20.dp))

            AnimatedContent(
                targetState = selectedTab,
                label = "enhanced_tab_switch",
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) }
            ) { tab ->
                when (tab) {
                    0 -> EnhancedOverviewTab(state)
                    1 -> ThreatAnalyticsTab(state)
                    2 -> AdvancedSettingsTab(state, onStateChange = { state = it })
                    else -> EnhancedOverviewTab(state)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ═══════════════════════════════════════════════
// 🎪 ENHANCED HEADER WITH THREAT RADAR
// ═══════════════════════════════════════════════
@Composable
fun EnhancedHeader(state: EnhancedDashboardState) {
    val infinite = rememberInfiniteTransition(label = "enhanced_pulse")
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "enhanced_pulse_alpha"
    )

    val scoreColors = when {
        state.safetyScore >= 80 -> EnhancedNexusColors.safeGradient
        state.safetyScore >= 50 -> EnhancedNexusColors.warnGradient
        else -> EnhancedNexusColors.dangerGradient
    }

    NeonGlowBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        glowColor = scoreColors.first()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        listOf(EnhancedNexusColors.bgSurface, EnhancedNexusColors.bgDeep)
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(EnhancedNexusColors.neonGreen.copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        GlitchText(
                            if (state.ghostModeActive) "GHOST MODE ACTIVE" else "MONITORING",
                            fontSize = 11.sp,
                            color = EnhancedNexusColors.neonGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Phantom Nexus",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = EnhancedNexusColors.textPrimary,
                        letterSpacing = 2.sp
                    )
                    Text(
                        "Last scan · ${state.lastScanMinutesAgo} min ago",
                        fontSize = 12.sp,
                        color = EnhancedNexusColors.textSecondary
                    )
                }

                AdvancedCircularProgress(
                    progress = state.safetyScore / 100f,
                    primaryColor = scoreColors.first()
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════
// 📑 ENHANCED TABS
// ══════════════════════════════════��════════════
@Composable
fun DashboardTabs(selectedTab: Int, onTabChange: (Int) -> Unit) {
    val tabs = listOf("Overview", "Analytics", "Advanced")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            EnhancedTabButton(title, selectedTab == index) { onTabChange(index) }
        }
    }
}

@Composable
fun EnhancedTabButton(title: String, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) EnhancedNexusColors.neonPurple.copy(alpha = 0.3f) else Color.Transparent,
        animationSpec = tween(250),
        label = "tab_bg"
    )
    val borderColor = if (isSelected) EnhancedNexusColors.neonPurple else EnhancedNexusColors.cardBorder

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = tween(250),
        label = "tab_scale"
    )

    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .scale(scale),
        color = bgColor,
        border = BorderStroke(2.dp, borderColor)
    ) {
        Text(
            title,
            color = if (isSelected) EnhancedNexusColors.textPrimary else EnhancedNexusColors.textSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
        )
    }
}

// ═══════════════════════════════════════════════
// 🏠 ENHANCED OVERVIEW — Real threats + Radar
// ═══════════════════════════════════════════════
@Composable
fun EnhancedOverviewTab(state: EnhancedDashboardState) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // Threat Radar Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = EnhancedNexusColors.cardBg),
            border = BorderStroke(1.dp, EnhancedNexusColors.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Real-Time Threat Radar",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = EnhancedNexusColors.textPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                ThreatRadar()
                Text(
                    "${state.threatsBlocked} threats detected & blocked",
                    fontSize = 12.sp,
                    color = EnhancedNexusColors.neonPink,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickStatCard("Safety", "${state.safetyScore}%", EnhancedNexusColors.neonGreen, Modifier.weight(1f))
            QuickStatCard("VPN", if (state.vpnActive) "On" else "Off", EnhancedNexusColors.neonCyan, Modifier.weight(1f))
            QuickStatCard("CPU", "${(state.cpuUsage * 100).toInt()}%", EnhancedNexusColors.neonAmber, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Threat Levels
        Text(
            "Threat Distribution",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = EnhancedNexusColors.textPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        val threats = listOf(
            ThreatLevel("Critical", 12, Icons.Default.GppBad, EnhancedNexusColors.neonPink, 0.15f),
            ThreatLevel("High", 45, Icons.Default.Warning, EnhancedNexusColors.neonAmber, 0.38f),
            ThreatLevel("Medium", 38, Icons.Default.Info, EnhancedNexusColors.neonCyan, 0.32f),
            ThreatLevel("Low", 22, Icons.Default.CheckCircle, EnhancedNexusColors.neonGreen, 0.15f)
        )

        threats.forEach { threat ->
            ThreatLevelBar(threat)
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun QuickStatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = EnhancedNexusColors.cardBg),
        border = BorderStroke(1.dp, EnhancedNexusColors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 10.sp, color = EnhancedNexusColors.textSecondary)
        }
    }
}

@Composable
fun ThreatLevelBar(threat: ThreatLevel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = EnhancedNexusColors.cardBg),
        border = BorderStroke(1.dp, EnhancedNexusColors.cardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(threat.icon, contentDescription = threat.name, tint = threat.color, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(threat.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EnhancedNexusColors.textPrimary)
                }
                Text("${threat.count}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = threat.color)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = threat.percentage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = threat.color,
                trackColor = EnhancedNexusColors.cardBorder,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

// ═══════════════════════════════════════════════
// 📈 THREAT ANALYTICS WITH HEATMAP
// ═══════════════════════════════════════════════
@Composable
fun ThreatAnalyticsTab(state: EnhancedDashboardState) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            "24h Threat Timeline",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = EnhancedNexusColors.textPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = EnhancedNexusColors.cardBg),
            border = BorderStroke(1.dp, EnhancedNexusColors.cardBorder)
        ) {
            HeatmapChart(modifier = Modifier.fillMaxSize().padding(12.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Threat Categories",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = EnhancedNexusColors.textPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        val categories = listOf(
            "Malware" to 0.65f,
            "Phishing" to 0.42f,
            "Spyware" to 0.28f,
            "Trackers" to 0.89f,
            "PUPs" to 0.15f
        )

        categories.forEach { (name, percentage) ->
            CategoryBar(name, percentage)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun HeatmapChart(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cols = 24
        val rows = 4
        val cellWidth = size.width / cols
        val cellHeight = size.height / rows

        repeat(cols) { col ->
            repeat(rows) { row ->
                val intensity = ((col + row) % 5) / 5f
                drawRect(
                    color = EnhancedNexusColors.neonCyan.copy(alpha = intensity * 0.6f),
                    topLeft = Offset(col * cellWidth, row * cellHeight),
                    size = androidx.compose.ui.geometry.Size(cellWidth - 1f, cellHeight - 1f)
                )
            }
        }
    }
}

@Composable
fun CategoryBar(name: String, percentage: Float) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = EnhancedNexusColors.cardBg),
        border = BorderStroke(1.dp, EnhancedNexusColors.cardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(name, fontSize = 12.sp, color = EnhancedNexusColors.textPrimary)
                Text("${(percentage * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EnhancedNexusColors.neonPink)
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = percentage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = EnhancedNexusColors.neonPink,
                trackColor = EnhancedNexusColors.cardBorder,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

// ═══════════════════════════════════════════════
// ⚙️ ADVANCED SETTINGS
// ═══════════════════════════════════════════════
@Composable
fun AdvancedSettingsTab(state: EnhancedDashboardState, onStateChange: (EnhancedDashboardState) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            "Security Settings",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = EnhancedNexusColors.textPrimary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        AdvancedToggleSetting(
            title = "Ghost Mode",
            subtitle = "Hide all traces & fingerprints",
            icon = Icons.Default.VisibilityOff,
            checked = state.ghostModeActive,
            onCheckedChange = { onStateChange(state.copy(ghostModeActive = it)) }
        )

        AdvancedToggleSetting(
            title = "VPN Protection",
            subtitle = "Encrypt all network traffic",
            icon = Icons.Default.VpnLock,
            checked = state.vpnActive,
            onCheckedChange = { onStateChange(state.copy(vpnActive = it)) }
        )

        AdvancedToggleSetting(
            title = "Auto Scan",
            subtitle = "Background scanning every 30 min",
            icon = Icons.Default.Radar,
            checked = state.autoScanEnabled,
            onCheckedChange = { onStateChange(state.copy(autoScanEnabled = it)) }
        )

        AdvancedToggleSetting(
            title = "Notifications",
            subtitle = "Real-time threat alerts",
            icon = Icons.Default.Notifications,
            checked = state.notificationsEnabled,
            onCheckedChange = { onStateChange(state.copy(notificationsEnabled = it)) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "System Status",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = EnhancedNexusColors.textPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        SystemStatusItem("Encryption", state.encryptionLevel, EnhancedNexusColors.neonGreen)
        Spacer(modifier = Modifier.height(10.dp))
        SystemStatusItem("Battery Impact", "${(state.batteryUsage * 100).toInt()}%", EnhancedNexusColors.neonAmber)
    }
}

@Composable
fun AdvancedToggleSetting(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    NeonGlowBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        glowColor = if (checked) EnhancedNexusColors.neonPurple else Color.Transparent
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = EnhancedNexusColors.cardBg),
            border = BorderStroke(1.dp, EnhancedNexusColors.cardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(icon, contentDescription = title, tint = EnhancedNexusColors.neonPurple, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EnhancedNexusColors.textPrimary)
                        Text(subtitle, fontSize = 11.sp, color = EnhancedNexusColors.textSecondary)
                    }
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = EnhancedNexusColors.neonPurple,
                        checkedTrackColor = EnhancedNexusColors.neonPurple.copy(alpha = 0.4f),
                        uncheckedThumbColor = EnhancedNexusColors.textSecondary,
                        uncheckedTrackColor = EnhancedNexusColors.cardBorder
                    )
                )
            }
        }
    }
}

@Composable
fun SystemStatusItem(label: String, value: String, color: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = EnhancedNexusColors.cardBg),
        border = BorderStroke(1.dp, EnhancedNexusColors.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EnhancedNexusColors.textPrimary)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

// ═══════════════════════════════════════════════
// 👁️ PREVIEW
// ═══════════════════════════════════════════════
@Preview(showBackground = true, backgroundColor = 0xFF05070D)
@Composable
fun EnhancedPhantomNexusDashboardPreview() {
    MaterialTheme {
        EnhancedPhantomNexusDashboard()
    }
}
