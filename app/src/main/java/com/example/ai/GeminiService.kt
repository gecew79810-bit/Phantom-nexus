package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.ai.gemini.GeminiDebugEvent
import com.example.ai.gemini.GeminiDebugListener
import com.example.ai.gemini.GeminiErrorCode
import com.example.ai.gemini.GeminiFallbackState
import com.example.ai.gemini.GeminiTelemetryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class GeminiResponseResult(
    val isSuccess: Boolean,
    val text: String,
    val debugEvent: GeminiDebugEvent
)

class GeminiService(
    private val customApiKey: String? = null,
    private val context: Context? = null
) {
    companion object {
        const val MODEL_NAME = "gemini-3.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    fun addDebugListener(listener: GeminiDebugListener) {
        GeminiTelemetryManager.addListener(listener)
    }

    fun removeDebugListener(listener: GeminiDebugListener) {
        GeminiTelemetryManager.removeListener(listener)
    }

    fun getEffectiveApiKey(): String {
        if (!customApiKey.isNullOrBlank()) return customApiKey
        val savedKey = try {
            val prefs = context?.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
            prefs?.getString("gemini_api_key", null)
        } catch (_: Exception) { null }
        if (!savedKey.isNullOrBlank()) return savedKey

        return try {
            val key = BuildConfig.GEMINI_API_KEY
            if (key.isNotBlank() && key != "MY_GEMINI_API_KEY") key else ""
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun generateResponseWithDebug(
        userPrompt: String,
        systemContext: String = "",
        screenText: String = ""
    ): GeminiResponseResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val requestId = UUID.randomUUID().toString().take(8)
        val promptSnippet = userPrompt.trim().take(80)

        GeminiTelemetryManager.notifyCallInitiated(
            requestId = requestId,
            model = MODEL_NAME,
            promptSnippet = promptSnippet,
            isMultimodal = false
        )

        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) {
            val debugEvent = GeminiDebugEvent(
                id = requestId,
                timestamp = System.currentTimeMillis(),
                isMultimodal = false,
                model = MODEL_NAME,
                promptSnippet = promptSnippet,
                httpStatusCode = 0,
                errorCode = GeminiErrorCode.MISSING_API_KEY,
                rawErrorMessage = "GEMINI_API_KEY is empty or not configured",
                apiStatusDetail = "CONFIG_MISSING",
                latencyMs = 0L,
                fallbackState = GeminiFallbackState.ACTIONABLE_ERROR_FALLBACK,
                diagnosticRecoveryHint = "Please configure your GEMINI_API_KEY in Settings or AI Studio Secrets."
            )
            GeminiTelemetryManager.recordEvent(debugEvent)
            return@withContext GeminiResponseResult(
                isSuccess = false,
                text = "[GEMINI ERROR: MISSING_API_KEY] बॉस, जेमिनी API Key सेटिंग्स या Secrets में दर्ज नहीं है।\n💡 [FALLBACK STATE: ${debugEvent.fallbackState.label}]",
                debugEvent = debugEvent
            )
        }

        try {
            Log.d("NEXUS_FLOW", "[LLM_REQUEST] Prompt: $userPrompt")
            val systemInstruction = """
                You are MAX, the ultra-smart, loyal, articulate, and respectful AI Operating System companion for Android (Phantom Nexus).
                You possess deep real-world, technical, and general knowledge.
                Always provide high-quality, comprehensive, accurate, and deeply insightful answers to whatever the user asks.
                Fluently understand and communicate in Hindi, Hinglish, and English.
                When the user writes or speaks in Hindi, reply in polished, polite, natural Hindi (e.g. "जी बॉस, ...").
                Format your responses neatly with clear structure and bullet points where helpful.
                CRITICAL DIRECTIVE: Answer the user's specific query: "$userPrompt". Do NOT output device specs, hardware diagnostics, RAM, storage, or battery status unless the user explicitly requested device status or battery info.
                Background hardware context: $systemContext
                ${if (screenText.isNotBlank()) "Currently visible on user's screen: $screenText" else ""}
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", userPrompt))
                        })
                    })
                }
                put("contents", contentsArray)

                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemInstruction))
                    })
                })

                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 1024)
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val url = "$BASE_URL?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val parsedError = parseGeminiApiError(response.code, responseBody)
                val fallback = when (parsedError.errorCode) {
                    GeminiErrorCode.RESOURCE_EXHAUSTED -> GeminiFallbackState.OFFLINE_OLLAMA_FALLBACK
                    GeminiErrorCode.SERVICE_UNAVAILABLE -> GeminiFallbackState.EMBEDDED_NEURAL_BRAIN_FALLBACK
                    GeminiErrorCode.PERMISSION_DENIED -> GeminiFallbackState.ACTIONABLE_ERROR_FALLBACK
                    else -> GeminiFallbackState.EMBEDDED_NEURAL_BRAIN_FALLBACK
                }

                val debugEvent = GeminiDebugEvent(
                    id = requestId,
                    timestamp = System.currentTimeMillis(),
                    isMultimodal = false,
                    model = MODEL_NAME,
                    promptSnippet = promptSnippet,
                    httpStatusCode = response.code,
                    errorCode = parsedError.errorCode,
                    rawErrorMessage = parsedError.errorMessage,
                    apiStatusDetail = parsedError.statusDetail,
                    latencyMs = latency,
                    fallbackState = fallback,
                    diagnosticRecoveryHint = parsedError.recoveryHint
                )
                GeminiTelemetryManager.recordEvent(debugEvent)
                Log.e("GeminiService", debugEvent.toFormattedLog())

                return@withContext GeminiResponseResult(
                    isSuccess = false,
                    text = "[GEMINI ERROR ${response.code}: ${parsedError.errorCode.shortTitle}] ${parsedError.errorMessage}\n💡 [FALLBACK: ${fallback.label}]",
                    debugEvent = debugEvent
                )
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)

            val finishReason = firstCandidate?.optString("finishReason", "STOP") ?: "STOP"
            if (finishReason == "SAFETY") {
                val debugEvent = GeminiDebugEvent(
                    id = requestId,
                    timestamp = System.currentTimeMillis(),
                    isMultimodal = false,
                    model = MODEL_NAME,
                    promptSnippet = promptSnippet,
                    httpStatusCode = 200,
                    errorCode = GeminiErrorCode.SAFETY_BLOCKED,
                    rawErrorMessage = "Response blocked by Gemini safety filters (finishReason=SAFETY)",
                    apiStatusDetail = "SAFETY_FILTER_TRIGGERED",
                    latencyMs = latency,
                    fallbackState = GeminiFallbackState.ACTIONABLE_ERROR_FALLBACK,
                    diagnosticRecoveryHint = "Please rephrase the prompt to satisfy standard safety policies."
                )
                GeminiTelemetryManager.recordEvent(debugEvent)

                return@withContext GeminiResponseResult(
                    isSuccess = false,
                    text = "[GEMINI STATUS: SAFETY_BLOCKED] सामग्री को जेमिनी सुरक्षा नीतियों द्वारा ब्लॉक किया गया है।\n💡 [FALLBACK: ${debugEvent.fallbackState.label}]",
                    debugEvent = debugEvent
                )
            }

            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text")

            if (text.isNullOrBlank()) {
                val debugEvent = GeminiDebugEvent(
                    id = requestId,
                    timestamp = System.currentTimeMillis(),
                    isMultimodal = false,
                    model = MODEL_NAME,
                    promptSnippet = promptSnippet,
                    httpStatusCode = 200,
                    errorCode = GeminiErrorCode.EMPTY_RESPONSE,
                    rawErrorMessage = "Candidate parts array was empty or contained no text",
                    apiStatusDetail = "EMPTY_TEXT_PARTS",
                    latencyMs = latency,
                    fallbackState = GeminiFallbackState.EMBEDDED_NEURAL_BRAIN_FALLBACK,
                    diagnosticRecoveryHint = "Try simplifying the query or retrying."
                )
                GeminiTelemetryManager.recordEvent(debugEvent)

                return@withContext GeminiResponseResult(
                    isSuccess = false,
                    text = "[GEMINI STATUS: EMPTY_RESPONSE] जेमिनी से कोई टेक्स्ट सामग्री प्राप्त नहीं हुई।\n💡 [FALLBACK: ${debugEvent.fallbackState.label}]",
                    debugEvent = debugEvent
                )
            }

            val successEvent = GeminiDebugEvent(
                id = requestId,
                timestamp = System.currentTimeMillis(),
                isMultimodal = false,
                model = MODEL_NAME,
                promptSnippet = promptSnippet,
                httpStatusCode = 200,
                errorCode = GeminiErrorCode.OK,
                rawErrorMessage = null,
                apiStatusDetail = "SUCCESS_200",
                latencyMs = latency,
                fallbackState = GeminiFallbackState.NONE,
                diagnosticRecoveryHint = "Response verified and delivered."
            )
            GeminiTelemetryManager.recordEvent(successEvent)
            Log.d("NEXUS_FLOW", "[LLM_RESPONSE] ${text.trim().take(300)}")

            return@withContext GeminiResponseResult(
                isSuccess = true,
                text = text.trim(),
                debugEvent = successEvent
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val (errorCode, recovery) = categorizeException(e)
            val fallback = GeminiFallbackState.EMBEDDED_NEURAL_BRAIN_FALLBACK

            val debugEvent = GeminiDebugEvent(
                id = requestId,
                timestamp = System.currentTimeMillis(),
                isMultimodal = false,
                model = MODEL_NAME,
                promptSnippet = promptSnippet,
                httpStatusCode = if (e is SocketTimeoutException) 504 else -1,
                errorCode = errorCode,
                rawErrorMessage = e.localizedMessage ?: e.javaClass.simpleName,
                apiStatusDetail = e.javaClass.simpleName,
                latencyMs = latency,
                fallbackState = fallback,
                diagnosticRecoveryHint = recovery
            )
            GeminiTelemetryManager.recordEvent(debugEvent)
            Log.e("GeminiService", "Gemini call failed: ${debugEvent.toFormattedLog()}", e)

            return@withContext GeminiResponseResult(
                isSuccess = false,
                text = "[GEMINI ERROR: ${errorCode.shortTitle}] ${e.localizedMessage ?: "नेटवर्क विफलता"}\n💡 [FALLBACK: ${fallback.label}]",
                debugEvent = debugEvent
            )
        }
    }

    suspend fun generateResponse(
        userPrompt: String,
        systemContext: String = "",
        screenText: String = ""
    ): String {
        return generateResponseWithDebug(userPrompt, systemContext, screenText).text
    }

    suspend fun generateMultimodalResponseWithDebug(
        userPrompt: String,
        bitmap: Bitmap,
        systemContext: String = ""
    ): GeminiResponseResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val requestId = UUID.randomUUID().toString().take(8)
        val promptSnippet = "[Multimodal Frame] " + userPrompt.trim().take(60)

        GeminiTelemetryManager.notifyCallInitiated(
            requestId = requestId,
            model = MODEL_NAME,
            promptSnippet = promptSnippet,
            isMultimodal = true
        )

        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) {
            val debugEvent = GeminiDebugEvent(
                id = requestId,
                timestamp = System.currentTimeMillis(),
                isMultimodal = true,
                model = MODEL_NAME,
                promptSnippet = promptSnippet,
                httpStatusCode = 0,
                errorCode = GeminiErrorCode.MISSING_API_KEY,
                rawErrorMessage = "GEMINI_API_KEY is empty for multimodal camera vision",
                apiStatusDetail = "CONFIG_MISSING",
                latencyMs = 0L,
                fallbackState = GeminiFallbackState.ACTIONABLE_ERROR_FALLBACK,
                diagnosticRecoveryHint = "Please configure GEMINI_API_KEY in Settings to enable multimodal visual reasoning."
            )
            GeminiTelemetryManager.recordEvent(debugEvent)
            return@withContext GeminiResponseResult(
                isSuccess = false,
                text = "[GEMINI ERROR: MISSING_API_KEY] कैमरा विज़न के लिए कृपया सेटिंग्स में अपनी GEMINI_API_KEY दर्ज करें।\n💡 [FALLBACK: ${debugEvent.fallbackState.label}]",
                debugEvent = debugEvent
            )
        }

        try {
            val systemInstruction = """
                You are MAX, an ultra-advanced multimodal AI Operating System companion for Android.
                You are analyzing a live camera visual frame provided by the user.
                Provide an accurate, intelligent, and concise observation or answer to the user's question.
                You understand Hindi, Hinglish, and English fluently.
                If the user speaks Hindi, reply politely in natural Hindi/Hinglish (e.g., 'बॉस, मुझे कैमरे में... दिख रहा है।').
                Keep answers crisp and informative for voice output.
                Context: $systemContext
            """.trimIndent()

            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
            val base64Image = Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.NO_WRAP)

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", userPrompt))
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        })
                    })
                }
                put("contents", contentsArray)

                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemInstruction))
                    })
                })

                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.4)
                    put("maxOutputTokens", 300)
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val url = "$BASE_URL?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val parsedError = parseGeminiApiError(response.code, responseBody)
                val fallback = when (parsedError.errorCode) {
                    GeminiErrorCode.RESOURCE_EXHAUSTED -> GeminiFallbackState.OFFLINE_OLLAMA_FALLBACK
                    GeminiErrorCode.PERMISSION_DENIED -> GeminiFallbackState.ACTIONABLE_ERROR_FALLBACK
                    else -> GeminiFallbackState.DIAGNOSTIC_BYPASS_PROTECTED
                }

                val debugEvent = GeminiDebugEvent(
                    id = requestId,
                    timestamp = System.currentTimeMillis(),
                    isMultimodal = true,
                    model = MODEL_NAME,
                    promptSnippet = promptSnippet,
                    httpStatusCode = response.code,
                    errorCode = parsedError.errorCode,
                    rawErrorMessage = parsedError.errorMessage,
                    apiStatusDetail = parsedError.statusDetail,
                    latencyMs = latency,
                    fallbackState = fallback,
                    diagnosticRecoveryHint = parsedError.recoveryHint
                )
                GeminiTelemetryManager.recordEvent(debugEvent)
                Log.e("GeminiService", "Multimodal API Error: ${debugEvent.toFormattedLog()}")

                return@withContext GeminiResponseResult(
                    isSuccess = false,
                    text = "[GEMINI MULTIMODAL ERROR ${response.code}: ${parsedError.errorCode.shortTitle}] ${parsedError.errorMessage}\n💡 [FALLBACK: ${fallback.label}]",
                    debugEvent = debugEvent
                )
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)

            val finishReason = firstCandidate?.optString("finishReason", "STOP") ?: "STOP"
            if (finishReason == "SAFETY") {
                val debugEvent = GeminiDebugEvent(
                    id = requestId,
                    timestamp = System.currentTimeMillis(),
                    isMultimodal = true,
                    model = MODEL_NAME,
                    promptSnippet = promptSnippet,
                    httpStatusCode = 200,
                    errorCode = GeminiErrorCode.SAFETY_BLOCKED,
                    rawErrorMessage = "Camera visual frame blocked by Gemini safety filters",
                    apiStatusDetail = "SAFETY_FILTER_TRIGGERED",
                    latencyMs = latency,
                    fallbackState = GeminiFallbackState.ACTIONABLE_ERROR_FALLBACK,
                    diagnosticRecoveryHint = "Frame contains imagery flagged by safety moderation."
                )
                GeminiTelemetryManager.recordEvent(debugEvent)

                return@withContext GeminiResponseResult(
                    isSuccess = false,
                    text = "[GEMINI STATUS: SAFETY_BLOCKED] कैमरा फ्रेम को सुरक्षा नीतियों द्वारा ब्लॉक किया गया है।",
                    debugEvent = debugEvent
                )
            }

            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text")

            if (text.isNullOrBlank()) {
                val debugEvent = GeminiDebugEvent(
                    id = requestId,
                    timestamp = System.currentTimeMillis(),
                    isMultimodal = true,
                    model = MODEL_NAME,
                    promptSnippet = promptSnippet,
                    httpStatusCode = 200,
                    errorCode = GeminiErrorCode.EMPTY_RESPONSE,
                    rawErrorMessage = "Visual frame recognized no objects or candidate was empty",
                    apiStatusDetail = "EMPTY_MULTIMODAL_OUTPUT",
                    latencyMs = latency,
                    fallbackState = GeminiFallbackState.DIAGNOSTIC_BYPASS_PROTECTED,
                    diagnosticRecoveryHint = "Ensure camera has clear lighting and focus."
                )
                GeminiTelemetryManager.recordEvent(debugEvent)

                return@withContext GeminiResponseResult(
                    isSuccess = false,
                    text = "[GEMINI STATUS: EMPTY_RESPONSE] विज़न फ्रेम में कोई स्पष्ट ऑब्जेक्ट नहीं पहचाना जा सका।",
                    debugEvent = debugEvent
                )
            }

            val successEvent = GeminiDebugEvent(
                id = requestId,
                timestamp = System.currentTimeMillis(),
                isMultimodal = true,
                model = MODEL_NAME,
                promptSnippet = promptSnippet,
                httpStatusCode = 200,
                errorCode = GeminiErrorCode.OK,
                rawErrorMessage = null,
                apiStatusDetail = "SUCCESS_200",
                latencyMs = latency,
                fallbackState = GeminiFallbackState.NONE,
                diagnosticRecoveryHint = "Visual analysis completed successfully."
            )
            GeminiTelemetryManager.recordEvent(successEvent)

            return@withContext GeminiResponseResult(
                isSuccess = true,
                text = text.trim(),
                debugEvent = successEvent
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val (errorCode, recovery) = categorizeException(e)
            val fallback = GeminiFallbackState.DIAGNOSTIC_BYPASS_PROTECTED

            val debugEvent = GeminiDebugEvent(
                id = requestId,
                timestamp = System.currentTimeMillis(),
                isMultimodal = true,
                model = MODEL_NAME,
                promptSnippet = promptSnippet,
                httpStatusCode = if (e is SocketTimeoutException) 504 else -1,
                errorCode = errorCode,
                rawErrorMessage = e.localizedMessage ?: e.javaClass.simpleName,
                apiStatusDetail = e.javaClass.simpleName,
                latencyMs = latency,
                fallbackState = fallback,
                diagnosticRecoveryHint = recovery
            )
            GeminiTelemetryManager.recordEvent(debugEvent)
            Log.e("GeminiService", "Multimodal call failed: ${debugEvent.toFormattedLog()}", e)

            return@withContext GeminiResponseResult(
                isSuccess = false,
                text = "[GEMINI MULTIMODAL ERROR: ${errorCode.shortTitle}] ${e.localizedMessage ?: "कैमरा फ्रेम प्रोसेस करने में विफलता"}\n💡 [FALLBACK: ${fallback.label}]",
                debugEvent = debugEvent
            )
        }
    }

    suspend fun generateMultimodalResponse(
        userPrompt: String,
        bitmap: Bitmap,
        systemContext: String = ""
    ): String {
        return generateMultimodalResponseWithDebug(userPrompt, bitmap, systemContext).text
    }

    private data class ParsedApiError(
        val errorCode: GeminiErrorCode,
        val errorMessage: String,
        val statusDetail: String,
        val recoveryHint: String
    )

    private fun parseGeminiApiError(code: Int, body: String): ParsedApiError {
        var message = "HTTP error $code"
        var status = "HTTP_$code"

        try {
            if (body.isNotBlank()) {
                val json = JSONObject(body)
                val errorObj = json.optJSONObject("error")
                if (errorObj != null) {
                    message = errorObj.optString("message", message)
                    status = errorObj.optString("status", status)
                }
            }
        } catch (_: Exception) {}

        val (mappedCode, hint) = when (code) {
            400 -> Pair(
                GeminiErrorCode.INVALID_ARGUMENT,
                "Request payload format or parameters were rejected by Gemini API. Check prompt length and model options."
            )
            401 -> Pair(
                GeminiErrorCode.UNAUTHORIZED,
                "API credentials missing or rejected. Ensure your Gemini API Key is authorized."
            )
            403 -> Pair(
                GeminiErrorCode.PERMISSION_DENIED,
                "API Key is invalid, restricted, or Gemini API has not been activated in Google Cloud Console."
            )
            404 -> Pair(
                GeminiErrorCode.NOT_FOUND,
                "Model endpoint ($MODEL_NAME) was not found. Verify supported model version."
            )
            429 -> Pair(
                GeminiErrorCode.RESOURCE_EXHAUSTED,
                "Gemini free-tier quota/rate limit exceeded (RPM/RPD cap reached). Switch to offline engine or await quota reset."
            )
            500 -> Pair(
                GeminiErrorCode.INTERNAL_SERVER_ERROR,
                "Google Gemini servers encountered an unexpected internal fault. Retry shortly."
            )
            502 -> Pair(
                GeminiErrorCode.BAD_GATEWAY,
                "Upstream gateway proxy error connecting to Gemini API."
            )
            503 -> Pair(
                GeminiErrorCode.SERVICE_UNAVAILABLE,
                "Google Gemini servers are currently experiencing excessive traffic or maintenance."
            )
            504 -> Pair(
                GeminiErrorCode.GATEWAY_TIMEOUT,
                "Gateway timed out waiting for inference response from Gemini backend."
            )
            else -> Pair(
                GeminiErrorCode.UNKNOWN_ERROR,
                "Unclassified API error returned with HTTP status $code."
            )
        }

        return ParsedApiError(
            errorCode = mappedCode,
            errorMessage = message,
            statusDetail = status,
            recoveryHint = hint
        )
    }

    private fun categorizeException(e: Exception): Pair<GeminiErrorCode, String> {
        return when (e) {
            is SocketTimeoutException -> Pair(
                GeminiErrorCode.GATEWAY_TIMEOUT,
                "Request timed out waiting for server response after 45s. Check network stability."
            )
            is UnknownHostException -> Pair(
                GeminiErrorCode.NETWORK_FAILURE,
                "Unable to resolve generativelanguage.googleapis.com. Verify active Wi-Fi / mobile data connection."
            )
            is IOException -> Pair(
                GeminiErrorCode.NETWORK_FAILURE,
                "I/O connection interrupted while transmitting to Gemini API. Check network status."
            )
            else -> Pair(
                GeminiErrorCode.UNKNOWN_ERROR,
                "Local runtime error: ${e.localizedMessage}"
            )
        }
    }
}

