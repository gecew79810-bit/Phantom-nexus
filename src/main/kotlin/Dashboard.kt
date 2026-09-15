package com.phantomnexus

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 🎨 Custom Color Palette
object DashboardColors {
    val primaryGradient = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
    val secondaryGradient = listOf(Color(0xFFF093FB), Color(0xFFF5576C))
    val accentGradient = listOf(Color(0xFF4FACFE), Color(0xFF00F2FE))
    val darkBg = Color(0xFF0F1419)
    val cardBg = Color(0xFF1A1F2E)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFFB0B9C1)
}

// 📊 Dashboard Data Model
data class DashboardMetric(
    val title: String,
    val value: String,
    val icon: ImageVector,
    val percentage: Float,
    val trend: String,
    val color: List<Color>
)

data class RecentActivity(
    val id: Int,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val timestamp: String,
    val status: String
)

// ⚙️ Main Dashboard Composable
@Composable
fun PhantomNexusDashboard() {
    var selectedTab by remember { mutableStateOf(0) }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DashboardColors.darkBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            DashboardHeader()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Tabs
            DashboardTabs(selectedTab) { selectedTab = it }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            when (selectedTab) {
                0 -> OverviewTab()
                1 -> AnalyticsTab()
                2 -> SettingsTab()
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun DashboardHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colors = DashboardColors.primaryGradient
                )
            )
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Phantom Nexus",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = DashboardColors.textPrimary
                )
                Text(
                    "Real-time OS Dashboard",
                    fontSize = 14.sp,
                    color = DashboardColors.textSecondary
                )
            }
            
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color = Color.White.copy(alpha = 0.2f)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    tint = DashboardColors.textPrimary
                )
            }
        }
    }
}

@Composable
fun DashboardTabs(selectedTab: Int, onTabChange: (Int) -> Unit) {
    val tabs = listOf("Overview", "Analytics", "Settings")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            TabButton(
                title = title,
                isSelected = selectedTab == index,
                onClick = { onTabChange(index) }
            )
        }
    }
}

@Composable
fun TabButton(title: String, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) 
            Color(0xFF667EEA) 
        else 
            Color.Transparent,
        animationSpec = tween(300)
    )
    
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(12.dp),
        color = backgroundColor
    ) {
        Text(
            title,
            color = if (isSelected) Color.White else DashboardColors.textSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun OverviewTab() {
    val metrics = listOf(
        DashboardMetric(
            title = "CPU Usage",
            value = "45%",
            icon = Icons.Default.Info,
            percentage = 45f,
            trend = "+2.5%",
            color = DashboardColors.primaryGradient
        ),
        DashboardMetric(
            title = "Memory",
            value = "62%",
            icon = Icons.Default.Info,
            percentage = 62f,
            trend = "-1.2%",
            color = DashboardColors.secondaryGradient
        ),
        DashboardMetric(
            title = "Network",
            value = "28%",
            icon = Icons.Default.Info,
            percentage = 28f,
            trend = "+5.8%",
            color = DashboardColors.accentGradient
        ),
        DashboardMetric(
            title = "Disk Space",
            value = "78%",
            icon = Icons.Default.Info,
            percentage = 78f,
            trend = "+0.3%",
            color = listOf(Color(0xFFFF6B6B), Color(0xFFFFA500))
        )
    )
    
    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        metrics.forEach { metric ->
            MetricCard(metric)
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Recent Activity Section
        Text(
            "Recent Activity",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = DashboardColors.textPrimary,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
        )
        
        RecentActivityList()
    }
}

@Composable
fun MetricCard(metric: DashboardMetric) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        color = DashboardColors.cardBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    metric.title,
                    fontSize = 14.sp,
                    color = DashboardColors.textSecondary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        metric.value,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = DashboardColors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        metric.trend,
                        fontSize = 12.sp,
                        color = if (metric.trend.startsWith("+")) 
                            Color(0xFF4CAF50) 
                        else 
                            Color(0xFF2196F3),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            ProgressCircle(
                percentage = metric.percentage,
                colors = metric.color,
                modifier = Modifier.size(80.dp)
            )
        }
    }
}

@Composable
fun ProgressCircle(
    percentage: Float,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Background circle
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(50%)),
            color = Color(0xFF2A3142)
        ) {}
        
        // Progress circle with gradient
        Surface(
            modifier = Modifier
                .fillMaxSize(percentage / 100f)
                .clip(RoundedCornerShape(50%))
                .background(Brush.linearGradient(colors)),
            color = Color.Transparent
        ) {}
        
        // Text
        Text(
            "${percentage.toInt()}%",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = DashboardColors.textPrimary
        )
    }
}

@Composable
fun RecentActivityList() {
    val activities = listOf(
        RecentActivity(
            id = 1,
            title = "System Update",
            description = "OS kernel updated to v5.15.2",
            icon = Icons.Default.Notifications,
            timestamp = "2 mins ago",
            status = "Completed"
        ),
        RecentActivity(
            id = 2,
            title = "Security Patch",
            description = "Applied critical security patches",
            icon = Icons.Default.Security,
            timestamp = "15 mins ago",
            status = "Completed"
        ),
        RecentActivity(
            id = 3,
            title = "Backup Process",
            description = "System backup in progress",
            icon = Icons.Default.Storage,
            timestamp = "1 hour ago",
            status = "In Progress"
        )
    )
    
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(activities) { activity ->
            ActivityItem(activity)
        }
    }
}

@Composable
fun ActivityItem(activity: RecentActivity) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .shadow(4.dp, RoundedCornerShape(12.dp)),
        color = DashboardColors.cardBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(DashboardColors.primaryGradient)
                    ),
                color = Color.Transparent
            ) {
                Icon(
                    imageVector = activity.icon,
                    contentDescription = activity.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    tint = Color.White
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    activity.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = DashboardColors.textPrimary
                )
                Text(
                    activity.description,
                    fontSize = 12.sp,
                    color = DashboardColors.textSecondary
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    activity.timestamp,
                    fontSize = 12.sp,
                    color = DashboardColors.textSecondary
                )
                Text(
                    activity.status,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (activity.status == "In Progress") 
                        Color(0xFFFFA500) 
                    else 
                        Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
fun AnalyticsTab() {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Text(
            "Performance Metrics",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = DashboardColors.textPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        // Chart placeholder
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(16.dp))
                .shadow(8.dp, RoundedCornerShape(16.dp)),
            color = DashboardColors.cardBg
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "📊 Analytics Chart\n(24-Hour View)",
                    fontSize = 16.sp,
                    color = DashboardColors.textSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SettingsTab() {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Text(
            "Dashboard Settings",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = DashboardColors.textPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        SettingItem("Theme", "Dark Mode", Icons.Default.Settings)
        SettingItem("Notifications", "Enabled", Icons.Default.Notifications)
        SettingItem("Auto Refresh", "30s", Icons.Default.Refresh)
        SettingItem("Language", "English", Icons.Default.Info)
    }
}

@Composable
fun SettingItem(title: String, value: String, icon: ImageVector) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .padding(bottom = 12.dp),
        color = DashboardColors.cardBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color(0xFF667EEA),
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DashboardColors.textPrimary
                    )
                    Text(
                        value,
                        fontSize = 12.sp,
                        color = DashboardColors.textSecondary
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.NavigateLast,
                contentDescription = "Navigate",
                tint = DashboardColors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
