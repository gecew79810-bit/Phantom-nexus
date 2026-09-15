package com.example.service.multimodal

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Camera Frame Analysis Result:
 * Captures live stream frame metadata, luminance, color statistics,
 * and high-resolution JPEG compressed payload for AI vision consumption.
 */
data class FrameAnalysisResult(
    val timestamp: Long = System.currentTimeMillis(),
    val width: Int = 0,
    val height: Int = 0,
    val averageLuminance: Float = 0f,
    val isLowLight: Boolean = false,
    val frameCount: Long = 0L,
    val description: String = ""
)

/**
 * Audio Stream Buffer Event:
 * Represents real-time PCM audio chunks streamed from local microphone pipeline
 * for low-latency voice-to-text inference and volume level detection.
 */
data class AudioStreamChunk(
    val timestamp: Long = System.currentTimeMillis(),
    val sampleRate: Int = 16000,
    val channelCount: Int = 1,
    val byteCount: Int = 0,
    val rmsLevel: Float = 0f,
    val isSpeechDetected: Boolean = false
)

/**
 * Multimodal Stream Pipeline State:
 * Central observability into live local camera & microphone streams.
 */
data class MultimodalPipelineState(
    val isCameraActive: Boolean = false,
    val isAudioPipelineActive: Boolean = false,
    val cameraFps: Float = 0f,
    val totalFramesCaptured: Long = 0L,
    val lastLuminance: Float = 0f,
    val audioRms: Float = 0f,
    val isVoiceActivityDetected: Boolean = false,
    val lastStatusMessage: String = "Multimodal pipeline idle"
)
