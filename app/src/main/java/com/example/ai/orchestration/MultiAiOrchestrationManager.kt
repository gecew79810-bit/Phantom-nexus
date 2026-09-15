package com.example.ai.orchestration

import android.content.Context
import android.util.Log
import com.example.ai.GeminiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MultiAiOrchestrationManager(
    private val context: Context,
    private val geminiService: GeminiService
) {
    companion object {
        private const val TAG = "JarvisMultiAi"
        private const val PREFS_NAME = "jarvis_multi_ai_prefs"
        private const val KEY_TOKENRA_KEY = "tokenra_api_key"
        private const val KEY_TOKENRA_URL = "tokenra_base_url"
        private const val KEY_ROUTING_PRESET = "routing_preset"
        private const val KEY_AUTO_FAILOVER = "auto_failover"
        private const val KEY_MULTI_AGENT = "multi_agent_mode"
        private const val KEY_SECOND_OPINION = "second_opinion_mode"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // StateFlows for UI Observability
    private val _routingPreset = MutableStateFlow(
        RoutingPreset.entries.find { it.name == prefs.getString(KEY_ROUTING_PRESET, RoutingPreset.AUTO.name) }
            ?: RoutingPreset.AUTO
    )
    val routingPreset: StateFlow<RoutingPreset> = _routingPreset.asStateFlow()

    private val _autoFailover = MutableStateFlow(prefs.getBoolean(KEY_AUTO_FAILOVER, true))
    val autoFailover: StateFlow<Boolean> = _autoFailover.asStateFlow()

    private val _multiAgentMode = MutableStateFlow(prefs.getBoolean(KEY_MULTI_AGENT, false))
    val multiAgentMode: StateFlow<Boolean> = _multiAgentMode.asStateFlow()

    private val _secondOpinionMode = MutableStateFlow(prefs.getBoolean(KEY_SECOND_OPINION, false))
    val secondOpinionMode: StateFlow<Boolean> = _secondOpinionMode.asStateFlow()

    private val _tokenRaApiKey = MutableStateFlow(prefs.getString(KEY_TOKENRA_KEY, "") ?: "")
    val tokenRaApiKey: StateFlow<String> = _tokenRaApiKey.asStateFlow()

    private val _tokenRaBaseUrl = MutableStateFlow(prefs.getString(KEY_TOKENRA_URL, "https://tokenra.io/v1") ?: "https://tokenra.io/v1")
    val tokenRaBaseUrl: StateFlow<String> = _tokenRaBaseUrl.asStateFlow()

    // Dynamic Registry of Models
    private val _models = MutableStateFlow<List<RegisteredModel>>(
        listOf(
            RegisteredModel(
                id = "gemini-2.5-flash",
                provider = ProviderType.GEMINI,
                displayName = "Google Gemini Flash",
                isPrimary = true,
                isFallback = false,
                supportsCoding = true,
                supportsReasoning = true,
                supportsLongContext = true,
                supportsVision = true,
                priority = 100
            ),
            RegisteredModel(
                id = "deepseek-chat",
                provider = ProviderType.TOKENRA,
                displayName = "DeepSeek Chat (TokenRa)",
                isPrimary = false,
                isFallback = true,
                supportsCoding = true,
                supportsReasoning = true,
                supportsLongContext = true,
                supportsVision = false,
                priority = 90
            ),
            RegisteredModel(
                id = "deepseek-reasoner",
                provider = ProviderType.TOKENRA,
                displayName = "DeepSeek Reasoner (TokenRa)",
                isPrimary = false,
                isFallback = true,
                supportsCoding = true,
                supportsReasoning = true,
                supportsLongContext = true,
                supportsVision = false,
                priority = 85
            ),
            RegisteredModel(
                id = "kimi-k1.5",
                provider = ProviderType.TOKENRA,
                displayName = "Moonshot Kimi (TokenRa)",
                isPrimary = false,
                isFallback = true,
                supportsCoding = true,
                supportsReasoning = true,
                supportsLongContext = true,
                supportsVision = false,
                priority = 80
            ),
            RegisteredModel(
                id = "glm-4-plus",
                provider = ProviderType.TOKENRA,
                displayName = "GLM 4 Plus (TokenRa)",
                isPrimary = false,
                isFallback = true,
                supportsCoding = true,
                supportsReasoning = true,
                supportsLongContext = true,
                supportsVision = true,
                priority = 75
            )
        )
    )
    val models: StateFlow<List<RegisteredModel>> = _models.asStateFlow()

    private val _lastFailoverLog = MutableStateFlow<String?>(null)
    val lastFailoverLog: StateFlow<String?> = _lastFailoverLog.asStateFlow()

    fun setRoutingPreset(preset: RoutingPreset) {
        _routingPreset.value = preset
        prefs.edit().putString(KEY_ROUTING_PRESET, preset.name).apply()
    }

    fun setAutoFailover(enabled: Boolean) {
        _autoFailover.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_FAILOVER, enabled).apply()
    }

    fun setMultiAgentMode(enabled: Boolean) {
        _multiAgentMode.value = enabled
        prefs.edit().putBoolean(KEY_MULTI_AGENT, enabled).apply()
    }

    fun setSecondOpinionMode(enabled: Boolean) {
        _secondOpinionMode.value = enabled
        prefs.edit().putBoolean(KEY_SECOND_OPINION, enabled).apply()
    }

    fun setTokenRaApiKey(key: String) {
        _tokenRaApiKey.value = key.trim()
        prefs.edit().putString(KEY_TOKENRA_KEY, key.trim()).apply()
    }

    fun setTokenRaBaseUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        _tokenRaBaseUrl.value = clean
        prefs.edit().putString(KEY_TOKENRA_URL, clean).apply()
    }

    fun toggleModelEnabled(modelId: String) {
        _models.value = _models.value.map {
            if (it.id == modelId) it.copy(isEnabled = !it.isEnabled) else it
        }
    }

    fun resetCircuitBreaker(modelId: String) {
        _models.value = _models.value.map {
            if (it.id == modelId) it.copy(
                circuitState = CircuitState.HEALTHY,
                failureCount = 0,
                circuitOpenUntilMs = 0
            ) else it
        }
    }

    fun setPrimaryModel(modelId: String) {
        _models.value = _models.value.map {
            it.copy(isPrimary = it.id == modelId, isFallback = it.id != modelId)
        }
    }

    /**
     * Resilient Multi-AI Turn Execution with Smart Failover & Bounded Retries
     */
    suspend fun executeResilientTurn(
        prompt: String,
        systemContext: String = ""
    ): MultiAiExecutionResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val failoverHistory = mutableListOf<String>()

        // 1. Task classification
        val isCoding = prompt.contains("code", true) || prompt.contains("function", true) || prompt.contains("kotlin", true)
        val isDeepReasoning = prompt.contains("why", true) || prompt.contains("derive", true) || prompt.contains("analyze", true)

        // 2. Select candidates
        val eligibleCandidates = _models.value.filter { it.isEnabled }.sortedWith { a, b ->
            val now = System.currentTimeMillis()
            val aHealthy = a.circuitState == CircuitState.HEALTHY || (a.circuitState == CircuitState.OPEN && now > a.circuitOpenUntilMs)
            val bHealthy = b.circuitState == CircuitState.HEALTHY || (b.circuitState == CircuitState.OPEN && now > b.circuitOpenUntilMs)

            if (aHealthy != bHealthy) {
                return@sortedWith if (aHealthy) -1 else 1
            }

            // Quality vs Speed bias
            when (_routingPreset.value) {
                RoutingPreset.QUALITY -> b.priority.compareTo(a.priority)
                RoutingPreset.FAST -> a.avgLatencyMs.compareTo(b.avgLatencyMs)
                else -> {
                    if (a.isPrimary != b.isPrimary) {
                        if (a.isPrimary) -1 else 1
                    } else {
                        b.priority.compareTo(a.priority)
                    }
                }
            }
        }

        // 3. Sequential candidate try with bounded failover
        var finalResultText: String? = null
        var chosenModel: RegisteredModel? = null
        var failoverHappened = false

        for (candidate in eligibleCandidates) {
            val modelId = candidate.id
            val provider = candidate.provider

            // Check circuit breaker status
            val now = System.currentTimeMillis()
            if (candidate.circuitState == CircuitState.OPEN && now < candidate.circuitOpenUntilMs) {
                failoverHistory.add("[Circuit Open] Skipped quarantined model $modelId")
                continue
            }

            try {
                if (provider == ProviderType.GEMINI) {
                    val geminiRes = geminiService.generateResponseWithDebug(prompt, systemContext)
                    if (geminiRes.isSuccess && geminiRes.text.isNotBlank()) {
                        recordModelSuccess(modelId, System.currentTimeMillis() - startMs)
                        finalResultText = geminiRes.text
                        chosenModel = candidate
                        break
                    } else {
                        val errCode = geminiRes.debugEvent.errorCode.httpCode
                        val errTitle = geminiRes.debugEvent.errorCode.shortTitle
                        recordModelFailure(modelId)
                        val failMsg = "[Gemini $errCode $errTitle] -> Failing over"
                        failoverHistory.add(failMsg)
                        _lastFailoverLog.value = failMsg
                        failoverHappened = true
                        if (!_autoFailover.value) {
                            finalResultText = geminiRes.text
                            chosenModel = candidate
                            break
                        }
                    }
                } else if (provider == ProviderType.TOKENRA) {
                    val tokenRaRes = callTokenRaChat(modelId, prompt, systemContext)
                    if (!tokenRaRes.isNullOrBlank()) {
                        recordModelSuccess(modelId, System.currentTimeMillis() - startMs)
                        finalResultText = tokenRaRes
                        chosenModel = candidate
                        break
                    } else {
                        recordModelFailure(modelId)
                        val failMsg = "[TokenRa $modelId empty/error] -> Trying next fallback"
                        failoverHistory.add(failMsg)
                        _lastFailoverLog.value = failMsg
                        failoverHappened = true
                    }
                }
            } catch (e: Exception) {
                recordModelFailure(modelId)
                val failMsg = "[${candidate.displayName} Exception: ${e.message?.take(50)}] -> Failover"
                failoverHistory.add(failMsg)
                _lastFailoverLog.value = failMsg
                failoverHappened = true
            }
        }

        // Review step if Second Opinion is enabled and primary response succeeded
        var reviewNotes: String? = null
        if (_secondOpinionMode.value && finalResultText != null) {
            try {
                val reviewerPrompt = "Briefly verify this output for accuracy and safety in 1 concise sentence:\n${finalResultText.take(500)}"
                val fallbackModel = _models.value.firstOrNull { it.isFallback && it.isEnabled }?.id ?: "deepseek-chat"
                val reviewerOutput = callTokenRaChat(fallbackModel, reviewerPrompt, "You are Jarvis Reviewer Agent.")
                if (!reviewerOutput.isNullOrBlank()) {
                    reviewNotes = "Verified by $fallbackModel: ${reviewerOutput.take(150)}"
                }
            } catch (_: Exception) { }
        }

        val totalLatency = System.currentTimeMillis() - startMs

        if (finalResultText != null && chosenModel != null) {
            return@withContext MultiAiExecutionResult(
                text = finalResultText,
                modelId = chosenModel.id,
                provider = chosenModel.provider,
                latencyMs = totalLatency,
                failoverOccurred = failoverHappened,
                failoverHistory = failoverHistory,
                reviewNotes = reviewNotes
            )
        }

        // Ultimate fallback
        return@withContext MultiAiExecutionResult(
            text = "बॉस, सभी ऑनलाइन AI मॉडल (Gemini और TokenRa) इस समय व्यस्त या अनुपलब्ध हैं। आंतरिक न्यूरल मोड सक्रिय है।",
            modelId = "embedded_neural",
            provider = ProviderType.LOCAL_OFFLINE,
            latencyMs = totalLatency,
            failoverOccurred = true,
            failoverHistory = failoverHistory
        )
    }

    /**
     * TokenRa OpenAI-compatible Chat Completions API invocation
     */
    private fun callTokenRaChat(modelId: String, prompt: String, systemInstruction: String): String? {
        val apiKey = _tokenRaApiKey.value
        if (apiKey.isBlank()) {
            Log.w(TAG, "TokenRa API Key is blank. Skipping TokenRa call.")
            return null
        }

        val endpoint = "${_tokenRaBaseUrl.value}/chat/completions"
        val messagesArray = JSONArray()

        if (systemInstruction.isNotBlank()) {
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", systemInstruction)
            })
        }

        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        })

        val payload = JSONObject().apply {
            put("model", modelId)
            put("messages", messagesArray)
            put("temperature", 0.2)
            put("max_tokens", 2048)
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "TokenRa HTTP Error: ${response.code} ${response.message}")
                return null
            }
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                return choices.getJSONObject(0).optJSONObject("message")?.optString("content")
            }
            return null
        }
    }

    private fun recordModelSuccess(modelId: String, latencyMs: Long) {
        _models.value = _models.value.map {
            if (it.id == modelId) {
                val newTotal = it.totalRequests + 1
                val newLatency = if (it.avgLatencyMs == 0L) latencyMs else (it.avgLatencyMs * 4 + latencyMs) / 5
                it.copy(
                    circuitState = CircuitState.HEALTHY,
                    failureCount = 0,
                    avgLatencyMs = newLatency,
                    totalRequests = newTotal
                )
            } else it
        }
    }

    private fun recordModelFailure(modelId: String) {
        _models.value = _models.value.map {
            if (it.id == modelId) {
                val newFailures = it.failureCount + 1
                val newTotal = it.totalRequests + 1
                val newCircuitState = if (newFailures >= 3) {
                    CircuitState.OPEN
                } else if (newFailures >= 2) {
                    CircuitState.DEGRADED
                } else {
                    CircuitState.HEALTHY
                }
                val openUntil = if (newCircuitState == CircuitState.OPEN) System.currentTimeMillis() + 60000 else 0L

                it.copy(
                    circuitState = newCircuitState,
                    failureCount = newFailures,
                    totalRequests = newTotal,
                    circuitOpenUntilMs = openUntil
                )
            } else it
        }
    }

    suspend fun testModelConnection(modelId: String): String = withContext(Dispatchers.IO) {
        val model = _models.value.find { it.id == modelId }
            ?: return@withContext "Model not found in registry."

        val start = System.currentTimeMillis()
        try {
            if (model.provider == ProviderType.GEMINI) {
                val res = geminiService.generateResponseWithDebug("Ping test for Jarvis Multi-AI system.")
                val lat = System.currentTimeMillis() - start
                if (res.isSuccess) {
                    recordModelSuccess(modelId, lat)
                    return@withContext "Success (${lat}ms): ${res.text.take(80)}"
                } else {
                    recordModelFailure(modelId)
                    return@withContext "Failed (${lat}ms): [${res.debugEvent.errorCode.shortTitle}]"
                }
            } else {
                val res = callTokenRaChat(modelId, "Ping test for Jarvis Multi-AI system.", "Jarvis test")
                val lat = System.currentTimeMillis() - start
                if (!res.isNullOrBlank()) {
                    recordModelSuccess(modelId, lat)
                    return@withContext "Success (${lat}ms): ${res.take(80)}"
                } else {
                    recordModelFailure(modelId)
                    return@withContext "Failed (${lat}ms): TokenRa returned empty or error"
                }
            }
        } catch (e: Exception) {
            recordModelFailure(modelId)
            return@withContext "Error: ${e.message}"
        }
    }
}
