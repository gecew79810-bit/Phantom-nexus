package com.pantham.nexus.decision.model

enum class DecisionConfidence {
    VERY_LOW,
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}

enum class DecisionRisk {
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class DecisionIntent {
    CHOOSE,
    COMPARE,
    RECOMMEND,
    RANK,
    EVALUATE,
    TRADEOFF,
    UNKNOWN
}

enum class EvidenceType {
    USER_CONSTRAINT,
    FILE,
    KNOWLEDGE,
    VISION,
    PREDICTION,
    SITUATION,
    EXTERNAL,
    EXPLICIT_USER_FACT,
    LEARNED_PREFERENCE,
    INFERRED
}

data class DecisionConstraint(
    val name: String,
    val value: String,
    val importance: Float = 0.5f,
    val hardConstraint: Boolean = false
)

data class DecisionEvidence(
    val type: EvidenceType,
    val sourceId: String? = null,
    val statement: String,
    val reliability: Float = 0.5f,
    val relevance: Float = 0.5f
) {
    val strength: Float
        get() = (
            reliability * 0.55f +
            relevance * 0.45f
        ).coerceIn(0f, 1f)
}

data class DecisionOption(
    val id: String,
    val name: String,
    val description: String = "",
    val attributes: Map<String, String> = emptyMap(),
    val evidence: List<DecisionEvidence> = emptyList()
)

data class DecisionCriterion(
    val id: String,
    val name: String,
    val weight: Float,
    val higherIsBetter: Boolean = true,
    val required: Boolean = false
)

data class DecisionScore(
    val optionId: String,
    val criterionId: String,
    val rawScore: Float,
    val weightedScore: Float,
    val explanation: String
)

data class DecisionRiskItem(
    val optionId: String,
    val description: String,
    val probability: Float,
    val impact: Float,
    val mitigation: String? = null
) {
    val severity: Float
        get() = (
            probability * impact
        ).coerceIn(0f, 1f)
}

data class DecisionTradeoff(
    val criterion: String,
    val preferredOptionId: String,
    val sacrificedOptionId: String,
    val explanation: String
)

data class DecisionAlternative(
    val optionId: String,
    val reason: String
)

data class DecisionRecommendation(
    val optionId: String?,
    val optionName: String?,
    val confidence: DecisionConfidence,
    val score: Float,
    val reason: String,
    val risks: List<DecisionRiskItem>,
    val tradeoffs: List<DecisionTradeoff>,
    val alternatives: List<DecisionAlternative>
)

data class MissingDecisionInformation(
    val field: String,
    val reason: String,
    val importance: Float,
    val blocking: Boolean
)

data class DecisionRequest(
    val query: String,
    val options: List<DecisionOption> = emptyList(),
    val constraints: List<DecisionConstraint> = emptyList(),
    val criteria: List<DecisionCriterion> = emptyList(),
    val evidence: List<DecisionEvidence> = emptyList(),
    val intent: DecisionIntent = DecisionIntent.UNKNOWN,
    val requireExplanation: Boolean = true,
    val maxAlternatives: Int = 2
)

data class DecisionAnalysis(
    val request: DecisionRequest,
    val scores: List<DecisionScore>,
    val risks: List<DecisionRiskItem>,
    val tradeoffs: List<DecisionTradeoff>,
    val missingInformation: List<MissingDecisionInformation>,
    val recommendation: DecisionRecommendation?,
    val confidence: DecisionConfidence,
    val generatedAt: Long = System.currentTimeMillis()
)

data class DecisionContext(
    val recentDecisions: List<DecisionAnalysis> = emptyList(),
    val activeDecision: DecisionAnalysis? = null,
    val relevantFiles: List<String> = emptyList(),
    val relevantKnowledge: List<String> = emptyList()
)
