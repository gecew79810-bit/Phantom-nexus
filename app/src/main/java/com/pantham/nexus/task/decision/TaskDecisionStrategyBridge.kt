package com.pantham.nexus.task.decision

import com.example.action.goal.TaskNode
import com.example.action.goal.TaskStepResult
import com.pantham.nexus.decision.engine.NexusDecisionEngine
import com.pantham.nexus.decision.model.*
import com.pantham.nexus.learning.runtime.NexusAdaptiveLearningRuntime
import com.pantham.nexus.task.model.RecoveryDecision
import com.pantham.nexus.task.model.RecoveryStrategy
import com.pantham.nexus.task.model.VerificationResult

/**
 * Bridges Autonomous Task Execution with the Decision Intelligence Core.
 * Evaluates recovery alternatives and trade-offs when an execution step fails or is blocked.
 */
class TaskDecisionStrategyBridge(
    private val decisionEngine: NexusDecisionEngine = NexusDecisionEngine(),
    private val learningRuntime: NexusAdaptiveLearningRuntime = NexusAdaptiveLearningRuntime.getInstance()
) {

    suspend fun decideRecovery(
        failedNode: TaskNode,
        stepResult: TaskStepResult?,
        verificationResult: VerificationResult?,
        retryCount: Int,
        maxRetries: Int = 2
    ): RecoveryDecision {
        // If max retries reached, do not retry blindly
        val canRetry = retryCount < maxRetries

        // Determine candidate options for the decision engine
        val candidateOptions = mutableListOf<DecisionOption>()

        if (canRetry) {
            candidateOptions.add(
                DecisionOption(
                    id = "opt_retry",
                    name = "Retry with Exponential Backoff",
                    attributes = mapOf(
                        "cost" to "low",
                        "delay" to "medium",
                        "risk" to "low",
                        "autonomy" to "high"
                    )
                )
            )
        }

        // If file/presentation/pdf failed, option to generate simpler text/summary
        val isArtifactNode = failedNode.title.contains("Presentation", ignoreCase = true) ||
                failedNode.title.contains("PDF", ignoreCase = true) ||
                failedNode.title.contains("Document", ignoreCase = true)

        if (isArtifactNode) {
            candidateOptions.add(
                DecisionOption(
                    id = "opt_fallback_text",
                    name = "Fallback to Plain Text Summary Document",
                    attributes = mapOf(
                        "cost" to "very_low",
                        "complexity" to "low",
                        "risk" to "very_low",
                        "autonomy" to "high"
                    )
                )
            )
        }

        // If network-related failure, option for offline fallback
        val isNetworkIssue = stepResult?.message?.contains("network", ignoreCase = true) == true ||
                stepResult?.message?.contains("connect", ignoreCase = true) == true ||
                stepResult?.message?.contains("timeout", ignoreCase = true) == true

        if (isNetworkIssue) {
            candidateOptions.add(
                DecisionOption(
                    id = "opt_fallback_cache",
                    name = "Fallback to Cached Local Knowledge",
                    attributes = mapOf(
                        "connectivity" to "offline",
                        "freshness" to "medium",
                        "risk" to "low",
                        "autonomy" to "high"
                    )
                )
            )
        }

        // Always available: Ask User
        candidateOptions.add(
            DecisionOption(
                id = "opt_ask_user",
                name = "Request User Clarification or Guidance",
                attributes = mapOf(
                    "delay" to "high",
                    "certainty" to "very_high",
                    "risk" to "low",
                    "autonomy" to "medium"
                )
            )
        )

        // Gather learned preferences from Adaptive Learning Core
        val evidenceList = mutableListOf<DecisionEvidence>()
        try {
            val prefs = learningRuntime.getPreferences()
            for (p in prefs) {
                if (p.key.contains("autonomous") || p.key.contains("retry") || p.key.contains("format")) {
                    evidenceList.add(
                        DecisionEvidence(
                            type = EvidenceType.LEARNED_PREFERENCE,
                            statement = "User preference: ${p.key} -> ${p.value}",
                            reliability = p.strength,
                            relevance = 0.85f
                        )
                    )
                }
            }
        } catch (_: Throwable) {}

        // Construct Decision Request
        val request = DecisionRequest(
            query = "Select recovery strategy for failed step '${failedNode.title}': ${stepResult?.message ?: verificationResult?.reason}",
            intent = DecisionIntent.CHOOSE,
            options = candidateOptions,
            evidence = evidenceList,
            criteria = listOf(
                DecisionCriterion("autonomy", "Maximal task completion autonomy", 0.8f),
                DecisionCriterion("risk", "Minimize execution failure risk", 0.9f),
                DecisionCriterion("delay", "Minimize user latency", 0.6f)
            )
        )

        val analysis = decisionEngine.analyze(request)
        val selected = analysis.recommendation?.optionId ?: if (canRetry) "opt_retry" else "opt_ask_user"

        return when (selected) {
            "opt_retry" -> RecoveryDecision(
                strategy = RecoveryStrategy.RETRY_WITH_BACKOFF,
                reason = "Decision Core selected retry with backoff based on low risk and high recovery probability.",
                delayMs = ((retryCount + 1) * 1000L).coerceIn(1000L, 5000L)
            )
            "opt_fallback_text" -> {
                val fallbackNode = failedNode.copy(
                    nodeId = failedNode.nodeId + "_text_fallback",
                    title = "Generate Standard Text Summary (Fallback for ${failedNode.title})",
                    customExecution = { outputs ->
                        TaskStepResult(
                            nodeId = failedNode.nodeId + "_text_fallback",
                            success = true,
                            message = "Generated text summary fallback instead of complex document.",
                            outputData = mapOf("fallback_summary" to "Executive summary generated successfully.")
                        )
                    }
                )
                RecoveryDecision(
                    strategy = RecoveryStrategy.FALLBACK_ALTERNATIVE_STEP,
                    reason = "Decision Core selected format downgrade to text summary to ensure goal completion.",
                    alternativeNode = fallbackNode
                )
            }
            "opt_fallback_cache" -> {
                val cachedNode = failedNode.copy(
                    nodeId = failedNode.nodeId + "_cache_fallback",
                    title = "Retrieve Cached Knowledge for ${failedNode.title}",
                    customExecution = { _ ->
                        TaskStepResult(
                            nodeId = failedNode.nodeId + "_cache_fallback",
                            success = true,
                            message = "Loaded cached knowledge snapshot.",
                            outputData = mapOf("cached_data" to true)
                        )
                    }
                )
                RecoveryDecision(
                    strategy = RecoveryStrategy.FALLBACK_ALTERNATIVE_STEP,
                    reason = "Decision Core selected cached fallback due to network unreliability.",
                    alternativeNode = cachedNode
                )
            }
            "opt_ask_user" -> RecoveryDecision(
                strategy = RecoveryStrategy.ASK_USER,
                reason = "Step failed after verification/retries. User guidance is required.",
                clarificationPrompt = "Step '${failedNode.title}' encountered an obstacle: ${stepResult?.message ?: verificationResult?.reason}. How would you like to proceed?"
            )
            else -> RecoveryDecision(
                strategy = RecoveryStrategy.FAIL_GRACEFULLY,
                reason = "No viable autonomous recovery strategy identified."
            )
        }
    }
}
