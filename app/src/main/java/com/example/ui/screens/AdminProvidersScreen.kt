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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.orchestration.CircuitState
import com.example.ai.orchestration.MultiAiOrchestrationManager
import com.example.ai.orchestration.RegisteredModel
import com.example.ai.orchestration.RoutingPreset
import com.example.ui.theme.AmberNeon
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.MagentaNeon
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletNeon
import kotlinx.coroutines.launch

@Composable
fun AdminProvidersScreen(
    orchestratorManager: MultiAiOrchestrationManager,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val models by orchestratorManager.models.collectAsState()
    val activePreset by orchestratorManager.routingPreset.collectAsState()
    val isAutoFailover by orchestratorManager.autoFailover.collectAsState()
    val isMultiAgent by orchestratorManager.multiAgentMode.collectAsState()
    val isSecondOpinion by orchestratorManager.secondOpinionMode.collectAsState()
    val tokenRaKey by orchestratorManager.tokenRaApiKey.collectAsState()
    val tokenRaUrl by orchestratorManager.tokenRaBaseUrl.collectAsState()
    val lastFailover by orchestratorManager.lastFailoverLog.collectAsState()

    var tokenRaKeyInput by remember(tokenRaKey) { mutableStateOf(tokenRaKey) }
    var tokenRaUrlInput by remember(tokenRaUrl) { mutableStateOf(tokenRaUrl) }
    var testResultMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var testingModelId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DeepSpaceBlack,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(DeepSpaceBlack)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("admin_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = CyanNeon
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "JARVIS MULTI-AI ADMIN",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = CyanNeon
                    )
                    Text(
                        text = "Orchestrator, Circuit Breakers & Fallbacks",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("admin_providers_screen")
        ) {
            // Section 1: Failover Alert Banner
            item {
                if (lastFailover != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AmberNeon.copy(alpha = 0.15f))
                        .border(1.dp, AmberNeon, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Recent Failover Event:\n$lastFailover",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = AmberNeon
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Section 1: Routing Presets
        item {
            Text(
                text = "ROUTING PRESETS",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MagentaNeon,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoutingPreset.entries.forEach { preset ->
                    val isSelected = preset == activePreset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) CyanNeon.copy(alpha = 0.2f) else GlassSurface)
                            .border(1.dp, if (isSelected) CyanNeon else GlassBorder, RoundedCornerShape(8.dp))
                            .clickable { orchestratorManager.setRoutingPreset(preset) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = preset.title,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (isSelected) CyanNeon else TextSecondary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section 2: Core Policies
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatic Failover", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Auto-switch to TokenRa if Gemini is exhausted/down", color = TextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isAutoFailover,
                            onCheckedChange = { orchestratorManager.setAutoFailover(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldNeon, checkedTrackColor = EmeraldNeon.copy(alpha = 0.4f))
                        )
                    }

                    HorizontalDivider(color = GlassBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Multi-Agent Mode", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Planner -> Specialist -> Reviewer pipeline", color = TextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isMultiAgent,
                            onCheckedChange = { orchestratorManager.setMultiAgentMode(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = VioletNeon, checkedTrackColor = VioletNeon.copy(alpha = 0.4f))
                        )
                    }

                    HorizontalDivider(color = GlassBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Second Opinion / Review", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Independent model checks answers for safety", color = TextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isSecondOpinion,
                            onCheckedChange = { orchestratorManager.setSecondOpinionMode(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = AmberNeon, checkedTrackColor = AmberNeon.copy(alpha = 0.4f))
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section 3: TokenRa Gateway Configuration
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "TOKENRA GATEWAY (OPENAI-COMPATIBLE)",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = VioletNeon
                    )

                    OutlinedTextField(
                        value = tokenRaKeyInput,
                        onValueChange = { tokenRaKeyInput = it },
                        label = { Text("TokenRa Bearer API Key", fontSize = 11.sp) },
                        placeholder = { Text("sk-...", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VioletNeon,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("tokenra_key_input")
                    )

                    OutlinedTextField(
                        value = tokenRaUrlInput,
                        onValueChange = { tokenRaUrlInput = it },
                        label = { Text("TokenRa Base URL", fontSize = 11.sp) },
                        placeholder = { Text("https://tokenra.io/v1", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VioletNeon,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            orchestratorManager.setTokenRaApiKey(tokenRaKeyInput)
                            orchestratorManager.setTokenRaBaseUrl(tokenRaUrlInput)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VioletNeon.copy(alpha = 0.3f)),
                        modifier = Modifier.align(Alignment.End).testTag("save_tokenra_btn")
                    ) {
                        Text("Save Gateway Credentials", color = VioletNeon, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Section 4: Registered Models & Circuit Breakers
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI MODEL REGISTRY (${models.size})",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = EmeraldNeon
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(models) { model ->
            ModelCard(
                model = model,
                isTesting = testingModelId == model.id,
                testResult = testResultMap[model.id],
                onToggleEnabled = { orchestratorManager.toggleModelEnabled(model.id) },
                onSetPrimary = { orchestratorManager.setPrimaryModel(model.id) },
                onResetCircuit = { orchestratorManager.resetCircuitBreaker(model.id) },
                onTestModel = {
                    coroutineScope.launch {
                        testingModelId = model.id
                        val res = orchestratorManager.testModelConnection(model.id)
                        testResultMap = testResultMap + (model.id to res)
                        testingModelId = null
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
}

@Composable
fun ModelCard(
    model: RegisteredModel,
    isTesting: Boolean,
    testResult: String?,
    onToggleEnabled: () -> Unit,
    onSetPrimary: () -> Unit,
    onResetCircuit: () -> Unit,
    onTestModel: () -> Unit
) {
    val circuitColor = when (model.circuitState) {
        CircuitState.HEALTHY -> EmeraldNeon
        CircuitState.DEGRADED -> AmberNeon
        CircuitState.OPEN -> CrimsonNeon
        CircuitState.RECOVERING -> CyanNeon
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassSurface)
            .border(1.dp, if (model.isPrimary) CyanNeon else GlassBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    if (model.isPrimary) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CyanNeon.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("PRIMARY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                        }
                    }
                }

                Switch(
                    checked = model.isEnabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(checkedThumbColor = CyanNeon, checkedTrackColor = CyanNeon.copy(alpha = 0.4f))
                )
            }

            // Status and latency
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Status: ${model.circuitState.label}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = circuitColor
                )
                Text(
                    text = "Latency: ${if (model.avgLatencyMs > 0) "${model.avgLatencyMs}ms" else "--"}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
            }

            // Capabilities tags
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (model.supportsCoding) CapabilityChip("Code")
                if (model.supportsReasoning) CapabilityChip("Reason")
                if (model.supportsLongContext) CapabilityChip("LongCtx")
                if (model.supportsVision) CapabilityChip("Vision")
                if (model.supportsTools) CapabilityChip("Tools")
            }

            if (testResult != null) {
                Text(
                    text = "Test: $testResult",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (testResult.startsWith("Success")) EmeraldNeon else AmberNeon
                )
            }

            // Actions: Test, Set Primary, Reset Circuit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onTestModel,
                    enabled = !isTesting,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = CyanNeon)
                    } else {
                        Text("Test", fontSize = 11.sp, color = CyanNeon)
                    }
                }

                if (!model.isPrimary) {
                    OutlinedButton(
                        onClick = onSetPrimary,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Set Primary", fontSize = 11.sp, color = TextSecondary)
                    }
                }

                if (model.circuitState != CircuitState.HEALTHY) {
                    OutlinedButton(
                        onClick = onResetCircuit,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset Circuit", fontSize = 11.sp, color = EmeraldNeon)
                    }
                }
            }
        }
    }
}

@Composable
fun CapabilityChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(DeepSpaceBlack)
            .border(1.dp, GlassBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 9.sp, color = TextMuted)
    }
}
