package com.example.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.example.service.MaxNotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveMediaState(
    val title: String = "No Active Playback",
    val artist: String = "Spotify / YouTube / Media Ready",
    val appPackage: String = "",
    val isPlaying: Boolean = false,
    val lastAction: String = "Idle"
)

class MaxMediaManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    private val _mediaState = MutableStateFlow(ActiveMediaState())
    val mediaState: StateFlow<ActiveMediaState> = _mediaState.asStateFlow()

    init {
        refreshActiveSessions()
    }

    /**
     * Inspects active media sessions via MediaSessionManager (Android 10+ / API 29+)
     */
    fun refreshActiveSessions() {
        try {
            val listenerComponent = ComponentName(context, MaxNotificationListenerService::class.java)
            val controllers: List<MediaController>? = mediaSessionManager?.getActiveSessions(listenerComponent)

            val activeController = controllers?.firstOrNull()
            if (activeController != null) {
                val metadata = activeController.metadata
                val playbackState = activeController.playbackState

                val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Active Media"
                val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: activeController.packageName
                val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING

                _mediaState.value = ActiveMediaState(
                    title = title,
                    artist = artist,
                    appPackage = activeController.packageName,
                    isPlaying = isPlaying,
                    lastAction = if (isPlaying) "Playing" else "Paused"
                )
            }
        } catch (e: SecurityException) {
            Log.w("MaxMediaManager", "Notification listener permission required for detailed MediaSession inspection.")
        } catch (e: Exception) {
            Log.e("MaxMediaManager", "Error refreshing media sessions", e)
        }
    }

    /**
     * Universal Media Play/Pause toggle using active controller or Hardware Media Key intent broadcast
     */
    fun togglePlayPause(): Pair<Boolean, String> {
        val controller = getPrimaryController()
        if (controller != null) {
            val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            if (isPlaying) {
                controller.transportControls.pause()
                _mediaState.value = _mediaState.value.copy(isPlaying = false, lastAction = "Playback Paused")
                return Pair(true, "Paused ${_mediaState.value.title}")
            } else {
                controller.transportControls.play()
                _mediaState.value = _mediaState.value.copy(isPlaying = true, lastAction = "Playback Resumed")
                return Pair(true, "Playing ${_mediaState.value.title}")
            }
        }

        // Hardware media key fallback (works globally across Spotify, YouTube, Apple Music)
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        val newPlaying = !_mediaState.value.isPlaying
        _mediaState.value = _mediaState.value.copy(isPlaying = newPlaying, lastAction = if (newPlaying) "Playing" else "Paused")
        return Pair(true, "Toggled media playback")
    }

    fun play(): Pair<Boolean, String> {
        val controller = getPrimaryController()
        if (controller != null) {
            controller.transportControls.play()
            _mediaState.value = _mediaState.value.copy(isPlaying = true, lastAction = "Playing")
            return Pair(true, "Resumed playback")
        }
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        _mediaState.value = _mediaState.value.copy(isPlaying = true, lastAction = "Playing")
        return Pair(true, "Dispatched Play command")
    }

    fun pause(): Pair<Boolean, String> {
        val controller = getPrimaryController()
        if (controller != null) {
            controller.transportControls.pause()
            _mediaState.value = _mediaState.value.copy(isPlaying = false, lastAction = "Paused")
            return Pair(true, "Paused playback")
        }
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        _mediaState.value = _mediaState.value.copy(isPlaying = false, lastAction = "Paused")
        return Pair(true, "Dispatched Pause command")
    }

    fun skipToNext(): Pair<Boolean, String> {
        val controller = getPrimaryController()
        if (controller != null) {
            controller.transportControls.skipToNext()
            _mediaState.value = _mediaState.value.copy(lastAction = "Skipped to Next")
            return Pair(true, "Skipped to next track")
        }
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        _mediaState.value = _mediaState.value.copy(lastAction = "Next Track")
        return Pair(true, "Dispatched Next track command")
    }

    fun skipToPrevious(): Pair<Boolean, String> {
        val controller = getPrimaryController()
        if (controller != null) {
            controller.transportControls.skipToPrevious()
            _mediaState.value = _mediaState.value.copy(lastAction = "Skipped to Previous")
            return Pair(true, "Returned to previous track")
        }
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        _mediaState.value = _mediaState.value.copy(lastAction = "Previous Track")
        return Pair(true, "Dispatched Previous track command")
    }

    private fun getPrimaryController(): MediaController? {
        return try {
            val listenerComponent = ComponentName(context, MaxNotificationListenerService::class.java)
            mediaSessionManager?.getActiveSessions(listenerComponent)?.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Dispatches hardware media key broadcast with DOWN + UP events
     */
    private fun dispatchMediaKey(keyCode: Int) {
        val am = audioManager ?: return
        val eventTime = SystemClock.uptimeMillis()

        val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        am.dispatchMediaKeyEvent(downEvent)

        val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
        am.dispatchMediaKeyEvent(upEvent)
    }
}
