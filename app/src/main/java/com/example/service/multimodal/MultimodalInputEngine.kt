package com.example.service.multimodal

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * MultimodalInputEngine:
 * Central coordination service for real-time camera stream capture and low-latency audio processing.
 * Feeds live sensory data to MaxOrchestrator for autonomous visual recognition, voice interaction,
 * and contextual AI understanding.
 */
class MultimodalInputEngine(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    val cameraManager = RealTimeCameraStreamManager(context, coroutineScope)
    val audioPipeline = LowLatencyAudioPipeline(context, coroutineScope)

    private val _pipelineState = MutableStateFlow(MultimodalPipelineState())
    val pipelineState: StateFlow<MultimodalPipelineState> = _pipelineState.asStateFlow()

    init {
        // Wire observability of camera and audio streams
        coroutineScope.launch(Dispatchers.Default) {
            combine(
                cameraManager.isStreaming,
                audioPipeline.isRecording,
                cameraManager.fps,
                audioPipeline.currentRmsDb,
                audioPipeline.isSpeechDetected
            ) { isCam, isAud, fps, rms, speech ->
                MultimodalPipelineState(
                    isCameraActive = isCam,
                    isAudioPipelineActive = isAud,
                    cameraFps = fps,
                    totalFramesCaptured = cameraManager.latestAnalysis.value.frameCount,
                    lastLuminance = cameraManager.latestAnalysis.value.averageLuminance,
                    audioRms = rms,
                    isVoiceActivityDetected = speech,
                    lastStatusMessage = when {
                        isCam && isAud -> "Full Multimodal Pipeline Active (Vision + Audio)"
                        isCam -> "Vision Stream Active (${String.format("%.1f", fps)} FPS)"
                        isAud -> "Low-Latency Audio Active (RMS: ${rms.toInt()} dB)"
                        else -> "Multimodal Subsystems Standby"
                    }
                )
            }.collect { state ->
                _pipelineState.value = state
            }
        }
    }

    /**
     * Start the vision stream with a provided LifecycleOwner.
     */
    fun startVision(lifecycleOwner: LifecycleOwner) {
        cameraManager.startCameraStream(lifecycleOwner)
    }

    fun stopVision() {
        cameraManager.stopCameraStream()
    }

    /**
     * Start the low-latency audio capture stream.
     */
    fun startAudio(): Boolean {
        return audioPipeline.startAudioCapture()
    }

    fun stopAudio() {
        audioPipeline.stopAudioCapture()
    }

    /**
     * Snap the current live camera frame if available.
     */
    fun captureCurrentFrame(): Bitmap? {
        return cameraManager.latestFrame.value
    }
}
