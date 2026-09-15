package com.example.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenedCallRecord(
    val number: String,
    val timestamp: Long = System.currentTimeMillis(),
    val allowed: Boolean = true
)

/**
 * Native Android CallScreeningService for Phantom Nexus.
 * Provides incoming-call event detection, caller extraction, and hands-free call screening.
 */
@RequiresApi(Build.VERSION_CODES.N)
class MaxCallScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "MaxCallScreening"

        private val _lastScreenedCall = MutableStateFlow<ScreenedCallRecord?>(null)
        val lastScreenedCall: StateFlow<ScreenedCallRecord?> = _lastScreenedCall.asStateFlow()

        private val _isServiceBound = MutableStateFlow(false)
        val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

        var callFilterCallback: ((String) -> Boolean)? = null
    }

    override fun onCreate() {
        super.onCreate()
        _isServiceBound.value = true
        Log.i(TAG, "MaxCallScreeningService created and bound.")
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceBound.value = false
        Log.i(TAG, "MaxCallScreeningService destroyed.")
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle
        val rawNumber = handle?.schemeSpecificPart ?: "Unknown"
        Log.i(TAG, "Screening incoming call from: $rawNumber")

        val shouldAllow = callFilterCallback?.invoke(rawNumber) ?: true
        _lastScreenedCall.value = ScreenedCallRecord(
            number = rawNumber,
            timestamp = System.currentTimeMillis(),
            allowed = shouldAllow
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val response = CallResponse.Builder()
                .setDisallowCall(!shouldAllow)
                .setRejectCall(!shouldAllow)
                .setSilenceCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(!shouldAllow)
                .build()
            respondToCall(callDetails, response)
        }
    }
}
