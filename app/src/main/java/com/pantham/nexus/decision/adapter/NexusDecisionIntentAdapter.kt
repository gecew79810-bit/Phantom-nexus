package com.pantham.nexus.decision.adapter

import com.pantham.nexus.decision.model.DecisionIntent
import com.pantham.nexus.decision.parser.NexusDecisionQueryParser
import com.pantham.nexus.intelligence.model.IntentResult
import com.pantham.nexus.intelligence.model.IntentType

class NexusDecisionIntentAdapter(
    private val parser: NexusDecisionQueryParser = NexusDecisionQueryParser()
) {

    /**
     * Adapts canonical IntentResult and user query to a fine-grained DecisionIntent.
     * Canonical intent system remains authoritative: if the canonical engine classified
     * as an unrelated action (e.g. SEND_MESSAGE, MAKE_CALL, SYSTEM_SETTINGS),
     * decision domain will NOT override it unless canonical was RESEARCH, QUERY_INFO, or GENERAL_CHAT/UNKNOWN.
     */
    fun adapt(canonicalResult: IntentResult, query: String): DecisionIntent {
        val decisionCandidate = parser.detectIntent(query)

        return when (canonicalResult.type) {
            IntentType.RESEARCH -> {
                if (decisionCandidate != DecisionIntent.UNKNOWN) {
                    decisionCandidate
                } else {
                    DecisionIntent.COMPARE
                }
            }
            IntentType.QUERY_INFO, IntentType.GENERAL_CHAT, IntentType.UNKNOWN -> {
                decisionCandidate
            }
            else -> {
                // If canonical is an action intent (e.g., OPEN_APP, SEND_MESSAGE),
                // do not classify as decision unless query explicitly asks for comparison/choice
                if (decisionCandidate != DecisionIntent.UNKNOWN && isExplicitDecisionQuery(query)) {
                    decisionCandidate
                } else {
                    DecisionIntent.UNKNOWN
                }
            }
        }
    }

    private fun isExplicitDecisionQuery(query: String): Boolean {
        val lower = query.lowercase()
        return listOf("which", "better", "best", "compare", "vs", "versus", "recommend", "pros and cons", "kaun sa", "kaunsa", "तुलना", "बेहतर").any {
            lower.contains(it)
        }
    }
}
