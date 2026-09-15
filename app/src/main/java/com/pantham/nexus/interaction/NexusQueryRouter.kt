package com.pantham.nexus.interaction

import android.util.Log
import com.pantham.nexus.intelligence.NexusIntelligenceCore

/**
 * High-level routing decision for Pantham Nexus queries.
 *
 * CRITICAL RULE:
 * This class NEVER treats arbitrary questions as a device-status query.
 * UNKNOWN is NEVER mapped to DEVICE_STATUS.
 */
enum class NexusRoute {
    NORMAL_CONVERSATION,
    DEVICE_STATUS,
    ACTION,
    KNOWLEDGE,
    UNKNOWN
}

data class NexusRouteDecision(
    val route: NexusRoute,
    val originalQuery: String
)

class NexusQueryRouter(
    private val intelligenceCore: NexusIntelligenceCore? = null
) {
    companion object {
        private const val TAG = "NEXUS_ROUTING"
    }

    /**
     * Decide where the ORIGINAL user query should go.
     *
     * Device status must be selected ONLY for an actual
     * device/system-state request.
     */
    fun route(query: String): NexusRouteDecision {
        val original = query.trim()

        if (original.isBlank()) {
            Log.d(TAG, "[ROUTING] Empty query -> UNKNOWN")
            return NexusRouteDecision(
                route = NexusRoute.UNKNOWN,
                originalQuery = original
            )
        }

        /*
         * Explicit device/system status phrases.
         * These are intentionally narrow so normal queries are not misrouted.
         */
        if (isDeviceStatusQuery(original)) {
            Log.d(TAG, "[ROUTING] Query '$original' -> DEVICE_STATUS")
            return NexusRouteDecision(
                route = NexusRoute.DEVICE_STATUS,
                originalQuery = original
            )
        }

        /*
         * Explicit action requests (open app, torch, call, etc.).
         */
        if (isActionQuery(original)) {
            Log.d(TAG, "[ROUTING] Query '$original' -> ACTION")
            return NexusRouteDecision(
                route = NexusRoute.ACTION,
                originalQuery = original
            )
        }

        /*
         * Normal conversational/knowledge questions.
         *
         * IMPORTANT:
         * Unknown is NEVER converted to DEVICE_STATUS.
         */
        val route = if (looksLikeKnowledgeQuestion(original)) {
            NexusRoute.KNOWLEDGE
        } else {
            NexusRoute.NORMAL_CONVERSATION
        }

        Log.d(TAG, "[ROUTING] Query '$original' -> $route")
        return NexusRouteDecision(
            route = route,
            originalQuery = original
        )
    }

    fun isDeviceStatusQuery(text: String): Boolean {
        val q = text.lowercase()

        val patterns = listOf(
            // Battery
            "battery",
            "बैटरी",
            "बेटरी",
            "चार्ज",
            "charge",
            "कितना चार्ज",
            "चार्ज कितना",

            // RAM / memory
            "ram",
            "रैम",
            "फोन मेमोरी",
            "डिवाइस मेमोरी",

            // Storage
            "storage",
            "स्टोरेज",
            "phone space",
            "खाली जगह",
            "कितनी जगह खाली",
            "कितना स्टोरेज",

            // Network
            "network status",
            "नेटवर्क स्टेटस",
            "wifi connected",
            "wi-fi connected",
            "वाईफाई कनेक्ट",
            "internet connection",
            "इंटरनेट कनेक्शन",

            // Device
            "device status",
            "system status",
            "phone status",
            "device information",
            "फोन की जानकारी",
            "फोन का स्टेटस",
            "डिवाइस स्टेटस",
            "सिस्टम डायग्नोस्टिक",
            "सिस्टम चेक",

            // Android/version
            "android version",
            "android कौन सा version",
            "एंड्रॉयड वर्जन",

            // CPU/hardware
            "cpu usage",
            "processor usage",
            "cpu इस्तेमाल",
            "hardware status",
            "hardware जानकारी"
        )

        return patterns.any { pattern ->
            if (pattern.any { it.code > 127 }) {
                q.contains(pattern)
            } else {
                q.contains(pattern.lowercase())
            }
        }
    }

    fun isActionQuery(text: String): Boolean {
        val q = text.lowercase()

        val actionWords = listOf(
            "open ",
            "खोलो",
            "खोल दो",
            "बंद करो",
            "close ",
            "call ",
            "कॉल",
            "message ",
            "मैसेज",
            "send ",
            "भेजो",
            "play ",
            "चलाओ",
            "volume",
            "वॉल्यूम",
            "screenshot",
            "स्क्रीनशॉट",
            "remind me",
            "याद दिलाना",
            "टॉर्च",
            "torch",
            "flashlight",
            "whatsapp",
            "व्हाट्सएप"
        )

        return actionWords.any { q.contains(it) }
    }

    fun looksLikeKnowledgeQuestion(text: String): Boolean {
        val q = text.lowercase()

        val questionMarkers = listOf(
            "?",
            "क्या",
            "कौन",
            "कहाँ",
            "कब",
            "क्यों",
            "कैसे",
            "कितना",
            "कितने",
            "बताओ",
            "समझाओ",
            "what",
            "who",
            "where",
            "when",
            "why",
            "how",
            "which",
            "explain",
            "tell me",
            "capital",
            "राजधानी",
            "math",
            "+",
            "-",
            "*",
            "/",
            "="
        )

        return questionMarkers.any { q.contains(it) }
    }
}
