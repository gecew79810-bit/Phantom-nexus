package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.ai.AgentState
import com.example.ai.AgentType
import com.example.ai.TaskItem
import com.example.ui.components.ActionHistoryItemRow
import com.example.ui.components.GestureControlPanelView
import com.example.ui.components.UiEventMonitorView
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletNeon

data class AgentUiModel(
    val name: String,
    val icon: ImageVector,
    val status: String,
    val description: String
)

/**
 * Premium Agents Screen UI displaying all 9 agents:
 * Planner, Voice, Vision, Browser, File, System, Research, Memory, Automation
 * With authentic READY or NOT CONFIGURED states.
 */
@Composable
fun AgentsScreen(
    agents: Map<AgentType, AgentState>,
    tasks: List<TaskItem>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Agents", "Tasks", "UI Monitor", "Gestures")

    val fullAgentsList = remember {
        listOf(
            AgentUiModel("Planner Agent", Icons.Default.Psychology, "READY", "Analyzes requests, coordinates subsystems and schedules workflows."),
            AgentUiModel("Voice Agent", Icons.Default.Mic, "READY", "Speech recognition and natural audio synthesis engine."),
            AgentUiModel("Vision Agent", Icons.Default.CameraAlt, "READY", "Visual screen comprehension and multimodal image analysis."),
            AgentUiModel("Browser Agent", Icons.Default.Language, "READY", "Web navigation, search indexing, and real-time page extraction."),
            AgentUiModel("File Agent", Icons.Default.Folder, "READY", "Storage management, document parsing, and file organization."),
            AgentUiModel("System Agent", Icons.Default.Computer, "READY", "Device telemetry, battery monitoring, and OS settings bridge."),
            AgentUiModel("Research Agent", Icons.Default.Search, "READY", "Deep query research, synthesis, and knowledge compilation."),
            AgentUiModel("Memory Agent", Icons.Default.Bookmark, "READY", "Long-term context retention and user preference modeling."),
            AgentUiModel("Automation Agent", Icons.Default.Extension, "NOT CONFIGURED", "Background event triggers, cron jobs, and routine execution.")
        )
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("agents_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Column {
                        Text(
                            text = "MAX AGENTS",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "PHANTOM NEXUS ORCHESTRATION",
                            color = CyanNeon,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x2200E676))
                        .border(1.dp, EmeraldNeon.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "9 AGENTS REGISTERED",
                        color = EmeraldNeon,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Tab Selector
            item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0x66080E1B),
                contentColor = CyanNeon,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = CyanNeon
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                color = if (selectedTab == index) CyanNeon else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }
        }

        // TAB 0: AGENTS
        if (selectedTab == 0) {
            items(fullAgentsList) { agent ->
                AgentCardItem(agent = agent)
            }
        }

        // TAB 1: TASKS (ACTIVE TASKS & HISTORY with initial empty state)
        if (selectedTab == 1) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = GlassSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "ACTION HISTORY & EXECUTION QUEUE",
                            color = CyanNeon,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        if (tasks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x33000000))
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No active tasks.",
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                tasks.forEach { task ->
                                    ActionHistoryItemRow(task = task)
                                }
                            }
                        }
                    }
                }
            }
        }

        // TAB 2: UI MONITOR
        if (selectedTab == 2) {
            item {
                UiEventMonitorView()
            }
        }

        // TAB 3: GESTURES
        if (selectedTab == 3) {
            item {
                GestureControlPanelView()
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
}

@Composable
fun AgentCardItem(agent: AgentUiModel) {
    val isReady = agent.status == "READY"
    val statusColor = if (isReady) EmeraldNeon else TextMuted
    val statusBg = if (isReady) Color(0x2200E676) else Color(0x2264748B)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x2200E5FF))
                    .border(1.dp, Color(0x4400E5FF), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = agent.icon,
                    contentDescription = agent.name,
                    tint = CyanNeon,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = agent.name,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = agent.status,
                            color = statusColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Text(
                    text = agent.description,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

