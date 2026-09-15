package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ai.MaxOrchestrator
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Sync
import com.example.ai.gemini.GeminiDebugEvent
import com.example.ai.gemini.GeminiErrorCode
import com.example.ai.gemini.GeminiFallbackState
import com.example.ai.gemini.GeminiTelemetryManager
import com.example.ui.theme.AmberNeon
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

/**
 * MultimodalInputConsole:
 * Interactive console component for real-time camera stream capture and low-latency audio processing.
 * Features live viewfinder preview, frame telemetry (FPS, luminance), VAD audio wave monitor,
 * and one-touch AI visual reasoning via Gemini.
 */
@Composable
fun MultimodalInputConsole(
    orchestrator: MaxOrchestrator,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val engine = orchestrator.multimodalEngine
    val pipelineState by engine.pipelineState.collectAsState()
    val latestAnalysis by engine.cameraManager.latestAnalysis.collectAsState()
    val latestFrame by engine.cameraManager.latestFrame.collectAsState()
    val isStreaming by engine.cameraManager.isStreaming.collectAsState()
    val isRecordingAudio by engine.audioPipeline.isRecording.collectAsState()
    val currentRmsDb by engine.audioPipeline.currentRmsDb.collectAsState()
    val isSpeechDetected by engine.audioPipeline.isSpeechDetected.collectAsState()

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var aiAnalysisResult by remember { mutableStateOf<String?>(null) }
    var isAnalyzingWithAi by remember { mutableStateOf(false) }
    var activeDebugEvent by remember { mutableStateOf<GeminiDebugEvent?>(null) }
    val latestGlobalGeminiEvent by orchestrator.geminiLatestEvent.collectAsState()

    val hasCameraPermission = remember(context) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val hasAudioPermission = remember(context) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    // Auto-stop camera on dispose to preserve hardware battery
    DisposableEffect(Unit) {
        onDispose {
            engine.stopVision()
            engine.stopAudio()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Telemetry & Hardware Status Banner
        CrystallineGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            specularGleam = true
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
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isStreaming || isRecordingAudio) EmeraldNeon else AmberNeon)
                        )
                        Text(
                            text = "MULTIMODAL SENSORY PIPELINE",
                            color = CyanNeon,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = if (isStreaming) "STREAMING" else "STANDBY",
                        color = if (isStreaming) EmeraldNeon else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Camera: ${if (isStreaming) "${String.format("%.1f", pipelineState.cameraFps)} FPS" else "Inactive"}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Frames: ${latestAnalysis.frameCount}",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Audio: ${if (isRecordingAudio) "${currentRmsDb.toInt()} dB" else "Muted"}",
                        color = if (isSpeechDetected) EmeraldNeon else TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Live Camera Preview Viewfinder with Cyberpunk Overlays
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, if (isStreaming) CyanNeon.copy(alpha = 0.8f) else GlassBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF030712))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isStreaming && hasCameraPermission) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                previewViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera Inactive",
                            tint = CyanNeon.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (!hasCameraPermission) "Camera Permission Required" else "Camera Stream Standby",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap 'START CAMERA' below to activate real-time visual input.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                // Cyber Viewfinder Crosshairs & Reticle Overlay
                if (isStreaming) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Top Left Tag
                        Text(
                            text = "LUM: ${latestAnalysis.averageLuminance.toInt()}/255",
                            color = CyanNeon,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .background(Color(0x88000000), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )

                        // Top Right Tag
                        Text(
                            text = if (latestAnalysis.isLowLight) "LOW LIGHT (BOOST)" else "OPTIMAL EXPOSURE",
                            color = if (latestAnalysis.isLowLight) AmberNeon else EmeraldNeon,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(Color(0x88000000), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )

                        // Center Reticle
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .align(Alignment.Center)
                                .border(1.dp, CyanNeon.copy(alpha = 0.4f), CircleShape)
                        )
                    }
                }
            }
        }

        // Camera & Audio Control Panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Toggle Camera Stream Button
            Button(
                onClick = {
                    if (isStreaming) {
                        engine.stopVision()
                    } else {
                        engine.cameraManager.startCameraStream(
                            lifecycleOwner = lifecycleOwner,
                            lensFacing = lensFacing,
                            onSurfacePreviewReady = { preview ->
                                previewViewRef?.surfaceProvider?.let { preview.setSurfaceProvider(it) }
                            }
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isStreaming) CrimsonNeon else CyanNeon,
                    contentColor = DeepSpaceBlack
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1.2f)
                    .testTag("toggle_camera_stream_button")
            ) {
                Icon(
                    imageVector = if (isStreaming) Icons.Default.VideocamOff else Icons.Default.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isStreaming) "STOP CAMERA" else "START CAMERA",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Flip Camera Button
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                    if (isStreaming) {
                        engine.stopVision()
                        engine.cameraManager.startCameraStream(
                            lifecycleOwner = lifecycleOwner,
                            lensFacing = lensFacing,
                            onSurfacePreviewReady = { preview ->
                                previewViewRef?.surfaceProvider?.let { preview.setSurfaceProvider(it) }
                            }
                        )
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(GlassSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                    .testTag("flip_camera_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Flip Lens",
                    tint = CyanNeon
                )
            }

            // Toggle Audio Pipeline Button
            Button(
                onClick = {
                    if (isRecordingAudio) {
                        engine.stopAudio()
                    } else {
                        engine.startAudio()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecordingAudio) VioletNeon else ElectricBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("toggle_audio_pipeline_button")
            ) {
                Icon(
                    imageVector = if (isRecordingAudio) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isRecordingAudio) "AUDIO ON" else "START MIC",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Live Audio Volume Energy Meter (VAD)
        if (isRecordingAudio) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x221A237E))
                    .border(1.dp, VioletNeon.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = VioletNeon,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "LOW-LATENCY 16kHz PCM PIPELINE",
                            color = VioletNeon,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = if (isSpeechDetected) "VOICE DETECTED" else "LISTENING",
                        color = if (isSpeechDetected) EmeraldNeon else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                LinearProgressIndicator(
                    progress = { (currentRmsDb / 40f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isSpeechDetected) EmeraldNeon else CyanNeon,
                    trackColor = Color(0x33000000)
                )
            }
        }

        // Gemini Multimodal AI Perception Trigger
        Button(
            onClick = {
                val frame = engine.captureCurrentFrame()
                if (frame != null) {
                    isAnalyzingWithAi = true
                    aiAnalysisResult = null
                    scope.launch {
                        val result = orchestrator.geminiService.generateMultimodalResponseWithDebug(
                            userPrompt = "Describe what you see in this live camera frame, identify any objects, text, or notable features in crisp conversational detail.",
                            bitmap = frame
                        )
                        activeDebugEvent = result.debugEvent
                        isAnalyzingWithAi = false
                        if (result.isSuccess) {
                            aiAnalysisResult = result.text
                            orchestrator.addTask("Multimodal Vision Analysis Completed", com.example.ai.AgentType.SYSTEM)
                        } else {
                            orchestrator.addTask("Vision Analysis Failed: [${result.debugEvent.errorCode.shortTitle}]", com.example.ai.AgentType.SYSTEM)
                        }
                    }
                } else {
                    aiAnalysisResult = "Please start the camera stream first to capture frames for AI reasoning."
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon, contentColor = DeepSpaceBlack),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("ai_multimodal_perceive_button")
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isAnalyzingWithAi) "AI REASONING IN PROGRESS..." else "ANALYZE FRAME WITH GEMINI AI",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // AI Multimodal Output Box (Success State)
        aiAnalysisResult?.let { result ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x2200E676))
                    .border(1.dp, EmeraldNeon.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "AI MULTIMODAL OBSERVATION",
                        color = EmeraldNeon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = result,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Robust Gemini Debugging Listener Card (Captures error codes and active fallback states)
        val currentEvent = activeDebugEvent ?: latestGlobalGeminiEvent
        if (currentEvent != null) {
            if (!currentEvent.isSuccess) {
                // Caught Error & Active Fallback Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x2A1A050A))
                        .border(1.dp, CrimsonNeon.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                        .testTag("gemini_debug_error_container")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = "Gemini Error",
                                    tint = CrimsonNeon,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "GEMINI DEBUG LISTENER",
                                    color = CrimsonNeon,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CrimsonNeon.copy(alpha = 0.25f))
                                    .border(1.dp, CrimsonNeon.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "HTTP ${currentEvent.httpStatusCode} • ${currentEvent.errorCode.shortTitle}",
                                    color = CrimsonNeon,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Specific Error Message (Not generic battery/status message)
                        Text(
                            text = currentEvent.rawErrorMessage ?: currentEvent.errorCode.standardDescription,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        // Active Fallback State
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22FFB300))
                                .border(1.dp, AmberNeon.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Fallback Active",
                                        tint = AmberNeon,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "FALLBACK STATE: ${currentEvent.fallbackState.label.uppercase()}",
                                        color = AmberNeon,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = currentEvent.fallbackState.technicalSummary,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        // Recommended Diagnostic Action
                        Text(
                            text = "💡 Recovery: ${currentEvent.diagnosticRecoveryHint}",
                            color = CyanNeon,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )

                        // Telemetry Footer (Latency + Model)
                        Text(
                            text = "Model: ${currentEvent.model} • Latency: ${currentEvent.latencyMs}ms • Code: ${currentEvent.errorCode.httpCode}",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                // Success Telemetry Badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x1500E676))
                        .border(1.dp, EmeraldNeon.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = EmeraldNeon,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "GEMINI DEBUG: 200 OK • ${currentEvent.model} • ${currentEvent.latencyMs}ms",
                            color = EmeraldNeon,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
