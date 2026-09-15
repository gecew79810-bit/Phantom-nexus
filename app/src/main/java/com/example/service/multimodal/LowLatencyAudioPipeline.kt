package com.example.service.multimodal

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * LowLatencyAudioPipeline:
 * Provides continuous 16kHz 16-bit Mono PCM audio capture with sub-20ms buffer chunks,
 * Voice Activity Detection (VAD) energy calculation, and raw streaming compatible with
 * on-device / cloud voice-to-text inference engines and wake-word listeners.
 */
class LowLatencyAudioPipeline(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "LowLatencyAudio"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val SPEECH_THRESHOLD_DB = 14.0f
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _currentRmsDb = MutableStateFlow(0f)
    val currentRmsDb: StateFlow<Float> = _currentRmsDb.asStateFlow()

    private val _isSpeechDetected = MutableStateFlow(false)
    val isSpeechDetected: StateFlow<Boolean> = _isSpeechDetected.asStateFlow()

    // Stream of raw audio chunks emitted to subscribers (STT, wake-word engine, visualizers)
    private val _audioChunks = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 64)
    val audioChunks: SharedFlow<ByteArray> = _audioChunks.asSharedFlow()

    var onAudioChunkProcessed: ((ByteArray, AudioStreamChunk) -> Unit)? = null

    /**
     * Checks whether RECORD_AUDIO permission has been granted.
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Starts low-latency continuous PCM audio stream.
     */
    fun startAudioCapture(): Boolean {
        if (_isRecording.value) return true

        if (!hasPermission()) {
            Log.w(TAG, "Cannot start audio capture: RECORD_AUDIO permission not granted")
            return false
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid audio buffer size")
            return false
        }

        // Sub-20ms chunk size: 16000 samples/sec * 2 bytes/sample * 0.02 sec = 640 bytes
        val chunkSize = 640.coerceAtLeast(minBufferSize / 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                chunkSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            _isRecording.value = true

            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                val buffer = ByteArray(chunkSize)
                while (isActive && _isRecording.value) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readBytes > 0) {
                        val chunkCopy = buffer.copyOf(readBytes)

                        // Compute root-mean-square (RMS) volume and dB level
                        var sum = 0.0
                        var i = 0
                        while (i < readBytes - 1) {
                            // 16-bit little endian sample
                            val sample = (chunkCopy[i].toInt() and 0xFF) or (chunkCopy[i + 1].toInt() shl 8)
                            val shortSample = sample.toShort()
                            sum += shortSample * shortSample
                            i += 2
                        }
                        val sampleCount = readBytes / 2
                        val rms = if (sampleCount > 0) sqrt(sum / sampleCount) else 0.0
                        val db = if (rms > 1.0) (20 * log10(rms)).toFloat() else 0f
                        _currentRmsDb.value = db

                        val speechActive = db > SPEECH_THRESHOLD_DB
                        _isSpeechDetected.value = speechActive

                        val meta = AudioStreamChunk(
                            timestamp = System.currentTimeMillis(),
                            sampleRate = SAMPLE_RATE,
                            channelCount = 1,
                            byteCount = readBytes,
                            rmsLevel = db,
                            isSpeechDetected = speechActive
                        )

                        _audioChunks.tryEmit(chunkCopy)
                        onAudioChunkProcessed?.invoke(chunkCopy, meta)
                    }
                }
            }

            Log.i(TAG, "LowLatencyAudioPipeline started recording successfully.")
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting audio record", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting audio record", e)
            return false
        }
    }

    /**
     * Stops the audio recording stream and releases hardware audio resources.
     */
    fun stopAudioCapture() {
        try {
            _isRecording.value = false
            recordingJob?.cancel()
            recordingJob = null

            audioRecord?.let {
                if (it.state == AudioRecord.STATE_INITIALIZED) {
                    it.stop()
                }
                it.release()
            }
            audioRecord = null
            _currentRmsDb.value = 0f
            _isSpeechDetected.value = false
            Log.i(TAG, "LowLatencyAudioPipeline stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio pipeline", e)
        }
    }
}
