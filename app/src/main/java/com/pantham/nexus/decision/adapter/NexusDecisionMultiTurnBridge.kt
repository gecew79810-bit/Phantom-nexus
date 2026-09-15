package com.pantham.nexus.decision.adapter

import com.example.ai.dialogue.MultiTurnSessionManager
import com.example.voice.AssistantLanguage
import com.pantham.nexus.decision.model.MissingDecisionInformation

class NexusDecisionMultiTurnBridge {

    fun requestMissingInformation(
        sessionManager: MultiTurnSessionManager,
        missing: List<MissingDecisionInformation>,
        language: AssistantLanguage = AssistantLanguage.ENGLISH
    ): String? {
        val blockingItem = missing.firstOrNull { it.blocking } ?: missing.firstOrNull() ?: return null

        val prompt = when {
            blockingItem.field.contains("comparison options", ignoreCase = true) -> {
                if (language == AssistantLanguage.HINDI) {
                    "तुलना करने के लिए कृपया दूसरा विकल्प भी बताएं।"
                } else {
                    "Please provide another option to compare."
                }
            }
            blockingItem.field.contains("budget", ignoreCase = true) || blockingItem.field.contains("price", ignoreCase = true) -> {
                if (language == AssistantLanguage.HINDI) {
                    "आपका बजट कितना है?"
                } else {
                    "What is your budget?"
                }
            }
            else -> {
                if (language == AssistantLanguage.HINDI) {
                    "निर्णय लेने के लिए ${blockingItem.field} की जानकारी चाहिए।"
                } else {
                    "Could you specify your preference for ${blockingItem.field}?"
                }
            }
        }

        sessionManager.setAwaitingDecisionSlot(
            missingSlot = blockingItem.field,
            promptQuestion = prompt,
            targetEntity = "DECISION"
        )

        return prompt
    }
}
