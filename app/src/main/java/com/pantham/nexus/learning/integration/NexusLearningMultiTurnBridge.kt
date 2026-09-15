package com.pantham.nexus.learning.integration

import com.example.ai.dialogue.MultiTurnSessionManager
import com.example.ai.dialogue.MultiTurnState
import com.pantham.nexus.learning.controller.NexusLearningController

class NexusLearningMultiTurnBridge(
    private val controller: NexusLearningController = NexusLearningController()
) {

    fun detectExplicitPreference(input: String): Pair<String, String>? {
        val lower = input.lowercase().trim()

        return when {
            lower.contains("prefer vegetarian") || lower.contains("शाकाहारी") ||
                (lower.contains("vegetarian") && (lower.contains("prefer") || lower.contains("चाहिए"))) ->
                "diet" to "vegetarian"
            lower.contains("prefer upi") || lower.contains("upi prefer") || lower.contains("upi चाहिए") ->
                "payment" to "upi"
            lower.contains("prefer card") || lower.contains("card prefer") ->
                "payment" to "card"
            lower.contains("prefer hindi") ||
                (lower.contains("हिंदी") && (lower.contains("prefer") || lower.contains("चाहिए") || lower.contains("बोल"))) ->
                "language" to "Hindi"
            lower.contains("सस्ता वाला") || lower.contains("cheaper") || lower.contains("lower cost") ->
                "budget" to "low_cost"
            lower.contains("quality") && (lower.contains("important") || lower.contains("ज्यादा") || lower.contains("priority")) ->
                "priority" to "quality"
            else -> null
        }
    }

    suspend fun processTurnFeedback(
        input: String,
        sessionManager: MultiTurnSessionManager
    ): Boolean {
        val detected = detectExplicitPreference(input) ?: return false
        controller.recordExplicitPreference(detected.first, detected.second)
        return true
    }
}
