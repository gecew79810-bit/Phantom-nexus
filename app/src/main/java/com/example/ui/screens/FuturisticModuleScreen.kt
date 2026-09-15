package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.MaxOrchestrator
import com.example.ui.components.CrystallineGlassSurface
import com.example.ui.components.MultimodalInputConsole
import com.example.ui.components.NavigationDestination
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletNeon

@Composable
fun FuturisticModuleScreen(
    destination: NavigationDestination,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    orchestrator: MaxOrchestrator? = null
) {
    val title = when (destination) {
        NavigationDestination.VISION -> "VISION & OCR"
        NavigationDestination.AUTOMATIONS -> "AUTOMATIONS"
        NavigationDestination.DEVICES -> "CONNECTED DEVICES"
        NavigationDestination.DEVELOPER -> "DEVELOPER SUITE"
        NavigationDestination.NEWS -> "INTEL & NEWS"
        NavigationDestination.PROVIDERS -> "AI PROVIDERS"
        else -> destination.name
    }

    val icon = when (destination) {
        NavigationDestination.VISION -> Icons.Default.Visibility
        NavigationDestination.AUTOMATIONS -> Icons.Default.SmartToy
        NavigationDestination.DEVICES -> Icons.Default.Devices
        NavigationDestination.DEVELOPER -> Icons.Default.Code
        else -> Icons.Default.AutoAwesome
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DeepSpaceBlack,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(DeepSpaceBlack)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("module_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CyanNeon
                        )
                    }

                    Column {
                        Text(
                            text = title,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "MAX OS SUBSYSTEM",
                            color = CyanNeon,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x2200E676))
                        .border(1.dp, EmeraldNeon.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(EmeraldNeon)
                    )
                    Text(
                        text = "ONLINE",
                        color = EmeraldNeon,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Subsystem Telemetry Card
        item {
            CrystallineGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                specularGleam = true,
                noiseDensity = 1.0f
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFF1E2A44), Color(0xFF0C1424))))
                                    .border(1.dp, CyanNeon.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    tint = CyanNeon,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "STATUS PROTOCOL",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "SECURE & SYNCHRONIZED",
                                    color = EmeraldNeon,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x2200E5FF))
                            .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "MAX Subsystem initialized. All telemetry and neural bridge interfaces are operating normally. Listening for voice triggers and command inputs from the primary console.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // Live Multimodal Camera & Low-Latency Audio Stream Console
        if (destination == NavigationDestination.VISION && orchestrator != null) {
            item {
                MultimodalInputConsole(orchestrator = orchestrator)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
}
