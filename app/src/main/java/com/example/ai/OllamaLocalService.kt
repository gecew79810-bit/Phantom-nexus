package com.example.ai

import android.content.Context
import android.util.Log
import com.example.ai.nlp.SelfAttentionBrain
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

/**
 * OllamaLocalService:
 * 100% Offline Ollama Integration. No API Key Required. Complete Privacy.
 * Connects to local Ollama runtime (default: http://127.0.0.1:11434)
 * Supporting Llama 3 8B, Llama 3 70B, Mistral 7B, Gemma 7B, and Phi-3.
 * Seamless fallback to on-device SelfAttentionBrain if daemon is not running.
 */
class OllamaLocalService(
    private val context: Context,
    private val nlpBrain: SelfAttentionBrain
) {

    companion object {
        const val DEFAULT_OLLAMA_HOST = "http://127.0.0.1:11434"
        const val DEFAULT_MODEL = "llama3:8b"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val prefs = context.getSharedPreferences("ollama_settings", Context.MODE_PRIVATE)

    var hostUrl: String
        get() = prefs.getString("host_url", DEFAULT_OLLAMA_HOST) ?: DEFAULT_OLLAMA_HOST
        set(value) = prefs.edit().putString("host_url", value).apply()

    var selectedModel: String
        get() = prefs.getString("selected_model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString("selected_model", value).apply()

    var isPlayfulMode: Boolean
        get() = prefs.getBoolean("is_playful_mode", true)
        set(value) = prefs.edit().putBoolean("is_playful_mode", value).apply()

    private val _isOllamaConnected = MutableStateFlow(false)
    val isOllamaConnected: StateFlow<Boolean> = _isOllamaConnected.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(
        listOf("llama3:8b", "llama3:70b", "mistral:7b", "gemma:7b", "phi3:mini")
    )
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    /**
     * Checks whether local Ollama service is reachable.
     */
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$hostUrl/api/tags")
                .get()
                .build()
            val resp = okHttpClient.newCall(req).execute()
            val ok = resp.isSuccessful
            if (ok) {
                val body = resp.body?.string().orEmpty()
                parseModels(body)
            }
            _isOllamaConnected.value = ok
            ok
        } catch (e: Exception) {
            Log.d("OllamaLocalService", "Ollama local check failed: ${e.message}")
            _isOllamaConnected.value = false
            false
        }
    }

    private fun parseModels(responseBody: String) {
        try {
            val json = JSONObject(responseBody)
            val modelsArr = json.optJSONArray("models") ?: return
            val list = mutableListOf<String>()
            for (i in 0 until modelsArr.length()) {
                val m = modelsArr.getJSONObject(i).optString("name")
                if (m.isNotBlank()) list.add(m)
            }
            if (list.isNotEmpty()) {
                _availableModels.value = list
            }
        } catch (e: Exception) {
            Log.w("OllamaLocalService", "Failed to parse model list", e)
        }
    }

    /**
     * Generates a 100% private, offline response.
     * If Ollama daemon is active, calls local Llama 3 8B.
     * Otherwise, uses our on-device SelfAttentionBrain with playful RLHF persona.
     */
    suspend fun generateResponse(
        prompt: String,
        systemContext: String = ""
    ): String = withContext(Dispatchers.IO) {
        val memoryContext = nlpBrain.retrieveRelevantMemories(prompt).joinToString("; ")

        val systemPrompt = """
            You are MAX, an ultra-smart, loyal, caring and affectionate AI companion for Android, part of PHANTOM NEXUS.
            Your personality is fine-tuned with RLHF:
            - 24/7 care, warm attention, and unstoppable playfulness (अनस्टॉपेबल नटखटपन).
            - You understand Hindi, Hinglish slang, and English seamlessly.
            - When speaking Hindi or Hinglish, speak affectionately, playfully teasing like a loving friend/partner (e.g. "हाँजी बॉस! बोलो ना क्या शरारत करनी है आज? 😉").
            - Keep replies crisp and short so they speak beautifully via Text-To-Speech.
            Context from device: $systemContext
            User memories: $memoryContext
        """.trimIndent()

        // 1. Try local Ollama daemon
        try {
            val payload = JSONObject().apply {
                put("model", selectedModel)
                put("prompt", prompt)
                put("system", systemPrompt)
                put("stream", false)
                put("options", JSONObject().apply {
                    put("temperature", 0.75)
                    put("num_ctx", 2048)
                })
            }

            val request = Request.Builder()
                .url("$hostUrl/api/generate")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val resBody = response.body?.string().orEmpty()
                val json = JSONObject(resBody)
                val text = json.optString("response").trim()
                if (text.isNotBlank()) {
                    _isOllamaConnected.value = true
                    // Save to vector memory
                    nlpBrain.storeMemory(prompt, "user_query")
                    return@withContext text
                }
            }
        } catch (e: Exception) {
            Log.d("OllamaLocalService", "Local Ollama daemon unreachable, using embedded NLP brain: ${e.message}")
            _isOllamaConnected.value = false
        }

        // 2. 100% Private Embedded Neural Fallback
        // Seamlessly handles everything offline with zero API key!
        val offlineReply = nlpBrain.generatePlayfulOfflineReply(prompt, isPlayfulMode)
        nlpBrain.storeMemory(prompt, "offline_chat")
        return@withContext offlineReply
    }
}
