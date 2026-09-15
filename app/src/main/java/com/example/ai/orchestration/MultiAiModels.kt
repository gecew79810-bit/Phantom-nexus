package com.example.ai.orchestration

enum class RoutingPreset(val title: String, val description: String) {
    AUTO("Auto-Adaptive", "Intelligently balances capabilities, latency, and reliability"),
    QUALITY("Quality First", "Prioritizes highest quality reasoning and coding models"),
    FAST("Fast Chat", "Optimizes for lowest response latency"),
    LOW_COST("Cost Aware", "Selects capable budget-efficient models")
}

enum class ProviderType(val displayName: String) {
    GEMINI("Google Gemini (Primary)"),
    TOKENRA("TokenRa Multi-Model Gateway"),
    LOCAL_OFFLINE("Local Embedded Neural")
}

enum class CircuitState(val label: String) {
    HEALTHY("Healthy 🟢"),
    DEGRADED("Degraded 🟡"),
    OPEN("Quarantined / Open 🔴"),
    RECOVERING("Testing / Recovering 🔄")
}

data class RegisteredModel(
    val id: String,
    val provider: ProviderType,
    val displayName: String,
    val isPrimary: Boolean,
    val isFallback: Boolean,
    var isEnabled: Boolean = true,
    val supportsCoding: Boolean = true,
    val supportsReasoning: Boolean = true,
    val supportsLongContext: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsTools: Boolean = true,
    val priority: Int = 100,
    var circuitState: CircuitState = CircuitState.HEALTHY,
    var failureCount: Int = 0,
    var avgLatencyMs: Long = 0,
    var totalRequests: Int = 0,
    var circuitOpenUntilMs: Long = 0
)

data class MultiAiExecutionResult(
    val text: String,
    val modelId: String,
    val provider: ProviderType,
    val latencyMs: Long,
    val failoverOccurred: Boolean,
    val failoverHistory: List<String> = emptyList(),
    val reviewNotes: String? = null
)
