package com.example.ai.intelligence

import android.util.Log
import org.json.JSONObject

data class StructuredActionOutput(
    val response: String,
    val intent: String,
    val tool: String? = null,
    val arguments: Map<String, String> = emptyMap(),
    val requires_confirmation: Boolean = false
)

object StructuredAiParser {
    private const val TAG = "StructuredAiParser"

    fun parseAndValidate(rawText: String): StructuredActionOutput? {
        val trimmed = rawText.trim()
        val jsonString = extractJson(trimmed) ?: return null

        return try {
            val obj = JSONObject(jsonString)
            val response = obj.optString("response", "").ifBlank {
                obj.optString("text", "Action processed.")
            }
            val intent = obj.optString("intent", "CHAT")
            val tool = obj.optString("tool", null).takeIf { !it.isNullOrBlank() }
            val requiresConfirm = obj.optBoolean("requires_confirmation", false)

            val argsMap = mutableMapOf<String, String>()
            if (obj.has("arguments")) {
                val argsObj = obj.optJSONObject("arguments")
                if (argsObj != null) {
                    val keys = argsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        argsMap[k] = argsObj.optString(k, "")
                    }
                }
            }

            StructuredActionOutput(
                response = response,
                intent = intent,
                tool = tool,
                arguments = argsMap,
                requires_confirmation = requiresConfirm
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse structured JSON: ${e.message}")
            null
        }
    }

    private fun extractJson(text: String): String? {
        val startIdx = text.indexOf('{')
        val endIdx = text.lastIndexOf('}')
        return if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
            text.substring(startIdx, endIdx + 1)
        } else {
            null
        }
    }
}
