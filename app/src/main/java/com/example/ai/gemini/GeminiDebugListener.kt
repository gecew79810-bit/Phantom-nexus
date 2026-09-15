package com.example.ai.gemini

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * GeminiErrorCode:
 * Granular classification of Gemini API response statuses, HTTP errors,
 * networking conditions, safety filter triggers, and configuration faults.
 */
enum class GeminiErrorCode(
    val httpCode: Int,
    val shortTitle: String,
    val standardDescription: String
) {
    OK(200, "OK", "Request completed successfully."),
    INVALID_ARGUMENT(400, "INVALID_ARGUMENT", "Malformed request payload, invalid model parameter, or payload size limit exceeded."),
    UNAUTHORIZED(401, "UNAUTHORIZED", "Missing or malformed authorization credentials."),
    PERMISSION_DENIED(403, "PERMISSION_DENIED", "Gemini API key is invalid, restricted by IP/package, or the API is disabled in Cloud Console."),
    NOT_FOUND(404, "NOT_FOUND", "The requested model endpoint was not found or is currently deprecated."),
    RESOURCE_EXHAUSTED(429, "RESOURCE_EXHAUSTED", "Rate limit exceeded or free-tier quota exhausted (RPM/RPD limit reached)."),
    INTERNAL_SERVER_ERROR(500, "INTERNAL_SERVER_ERROR", "An unexpected internal error occurred on Google Gemini servers."),
    BAD_GATEWAY(502, "BAD_GATEWAY", "Bad gateway response received from Google Cloud proxy."),
    SERVICE_UNAVAILABLE(503, "SERVICE_UNAVAILABLE", "Gemini service is overloaded, experiencing high traffic, or temporarily unavailable."),
    GATEWAY_TIMEOUT(504, "GATEWAY_TIMEOUT", "Google Cloud gateway timed out waiting for Gemini inference engine."),
    NETWORK_FAILURE(-1, "NETWORK_FAILURE", "Unable to reach Gemini API (connection timeout, DNS failure, or device offline)."),
    SAFETY_BLOCKED(-2, "SAFETY_BLOCKED", "Content generation was blocked by Google Gemini Safety Filters."),
    MISSING_API_KEY(-3, "MISSING_API_KEY", "Gemini API key is blank or not configured in AI Studio Secrets / SharedPreferences."),
    JSON_PARSING_ERROR(-4, "JSON_PARSING_ERROR", "Response received from server could not be parsed as valid Gemini JSON schema."),
    EMPTY_RESPONSE(-5, "EMPTY_RESPONSE", "Gemini returned HTTP 200 but candidate response parts were empty or null."),
    UNKNOWN_ERROR(-99, "UNKNOWN_ERROR", "An unclassified exception occurred during Gemini API communication.")
}

/**
 * GeminiFallbackState:
 * Explicit fallback states activated when Gemini Cloud API analysis fails,
 * preventing accidental fallback to default battery/status messages.
 */
enum class GeminiFallbackState(
    val label: String,
    val technicalSummary: String
) {
    NONE(
        "Active Cloud Engine",
        "Primary Gemini 3.5 Flash cloud intelligence operating normally."
    ),
    OFFLINE_OLLAMA_FALLBACK(
        "Offline Ollama LLM Active",
        "Switched to local Ollama (Llama 3 8B) on device localhost:11434."
    ),
    EMBEDDED_NEURAL_BRAIN_FALLBACK(
        "Embedded Transformer Brain Active",
        "Switched to local Self-Attention Neural NLP Engine (100% offline INT8 quantized)."
    ),
    DIAGNOSTIC_BYPASS_PROTECTED(
        "Telemetry Dump Bypassed",
        "Suppressed default battery/hardware specs dump; returned precise AI diagnostic analysis."
    ),
    ACTIONABLE_ERROR_FALLBACK(
        "Actionable Recovery State",
        "Suppressed fallback; presenting exact API diagnostic recovery steps to user."
    )
}

/**
 * GeminiDebugEvent:
 * Detailed telemetry record for every Gemini API request, capturing request parameters,
 * latency, exact error code, error status detail, and fallback resolution.
 */
data class GeminiDebugEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val isMultimodal: Boolean,
    val model: String,
    val promptSnippet: String,
    val httpStatusCode: Int,
    val errorCode: GeminiErrorCode,
    val rawErrorMessage: String?,
    val apiStatusDetail: String?,
    val latencyMs: Long,
    val fallbackState: GeminiFallbackState,
    val diagnosticRecoveryHint: String
) {
    val isSuccess: Boolean
        get() = httpStatusCode in 200..299 && errorCode == GeminiErrorCode.OK

    fun toFormattedLog(): String {
        return buildString {
            append("[GEMINI DEBUG] ")
            if (isSuccess) {
                append("SUCCESS ($httpStatusCode OK) • Model: $model • ${latencyMs}ms")
            } else {
                append("ERROR (${errorCode.httpCode} ${errorCode.shortTitle}) • Model: $model • Latency: ${latencyMs}ms\n")
                append("  ├─ Reason: ${rawErrorMessage ?: errorCode.standardDescription}\n")
                if (!apiStatusDetail.isNullOrBlank()) {
                    append("  ├─ API Status: $apiStatusDetail\n")
                }
                append("  ├─ Fallback State: ${fallbackState.label} (${fallbackState.technicalSummary})\n")
                append("  └─ Action: $diagnosticRecoveryHint")
            }
        }
    }
}

/**
 * GeminiDebugListener:
 * Callback interface invoked for real-time telemetry observation.
 */
interface GeminiDebugListener {
    fun onApiCallInitiated(
        requestId: String,
        model: String,
        promptSnippet: String,
        isMultimodal: Boolean
    )

    fun onApiResponseSuccess(event: GeminiDebugEvent)

    fun onApiErrorCaught(event: GeminiDebugEvent)

    fun onFallbackActivated(event: GeminiDebugEvent, fallbackReason: String)
}

/**
 * GeminiTelemetryManager:
 * Central event bus and telemetry buffer that manages active listeners
 * and exposes reactive StateFlows for UI display.
 */
object GeminiTelemetryManager {
    private val listeners = mutableListOf<GeminiDebugListener>()

    private val _recentEvents = MutableStateFlow<List<GeminiDebugEvent>>(emptyList())
    val recentEvents: StateFlow<List<GeminiDebugEvent>> = _recentEvents.asStateFlow()

    private val _latestEvent = MutableStateFlow<GeminiDebugEvent?>(null)
    val latestEvent: StateFlow<GeminiDebugEvent?> = _latestEvent.asStateFlow()

    private val _latestError = MutableStateFlow<GeminiDebugEvent?>(null)
    val latestError: StateFlow<GeminiDebugEvent?> = _latestError.asStateFlow()

    @Synchronized
    fun addListener(listener: GeminiDebugListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    @Synchronized
    fun removeListener(listener: GeminiDebugListener) {
        listeners.remove(listener)
    }

    fun notifyCallInitiated(
        requestId: String,
        model: String,
        promptSnippet: String,
        isMultimodal: Boolean
    ) {
        synchronized(listeners) {
            listeners.forEach {
                try {
                    it.onApiCallInitiated(requestId, model, promptSnippet, isMultimodal)
                } catch (_: Exception) {}
            }
        }
    }

    fun recordEvent(event: GeminiDebugEvent) {
        _latestEvent.value = event
        if (!event.isSuccess) {
            _latestError.value = event
        }

        val updated = (_recentEvents.value.toMutableList()).apply {
            add(0, event)
            if (size > 30) removeAt(size - 1)
        }
        _recentEvents.value = updated

        synchronized(listeners) {
            listeners.forEach {
                try {
                    if (event.isSuccess) {
                        it.onApiResponseSuccess(event)
                    } else {
                        it.onApiErrorCaught(event)
                        if (event.fallbackState != GeminiFallbackState.NONE) {
                            it.onFallbackActivated(event, event.fallbackState.label)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun clearHistory() {
        _recentEvents.value = emptyList()
        _latestEvent.value = null
        _latestError.value = null
    }
}
