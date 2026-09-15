package com.pantham.nexus.task.recovery

import com.example.action.goal.TaskNode
import com.example.action.goal.TaskStepResult
import com.pantham.nexus.task.decision.TaskDecisionStrategyBridge
import com.pantham.nexus.task.model.BlockedReason
import com.pantham.nexus.task.model.RecoveryDecision
import com.pantham.nexus.task.model.RecoveryStrategy
import com.pantham.nexus.task.model.VerificationResult

/**
 * Handles execution recovery, automatic retries with backoff, alternative strategies,
 * and replanning when steps fail or verify negatively.
 */
class TaskRecoveryEngine(
    private val decisionBridge: TaskDecisionStrategyBridge = TaskDecisionStrategyBridge(),
    private val maxRetriesPerNode: Int = 2
) {

    suspend fun handleStepFailure(
        node: TaskNode,
        stepResult: TaskStepResult?,
        verificationResult: VerificationResult?,
        currentRetryCount: Int
    ): RecoveryDecision {
        // If step requires user clarification explicitly
        if (stepResult?.requiresUserClarification == true) {
            val prompt = stepResult.clarificationPrompt ?: "Please provide more details to proceed with '${node.title}'."
            return RecoveryDecision(
                strategy = RecoveryStrategy.ASK_USER,
                reason = "Step explicitly requested user clarification.",
                clarificationPrompt = prompt
            )
        }

        // Consult Decision Strategy Bridge to pick the optimal path
        return decisionBridge.decideRecovery(
            failedNode = node,
            stepResult = stepResult,
            verificationResult = verificationResult,
            retryCount = currentRetryCount,
            maxRetries = maxRetriesPerNode
        )
    }

    fun buildBlockedReason(
        node: TaskNode,
        decision: RecoveryDecision,
        stepResult: TaskStepResult?
    ): BlockedReason {
        val prompt = decision.clarificationPrompt
            ?: stepResult?.clarificationPrompt
            ?: "Step '${node.title}' requires your confirmation or additional input to continue."

        return BlockedReason(
            code = "STEP_BLOCKED_${node.nodeId}",
            description = decision.reason,
            clarificationPrompt = prompt,
            requiredInputType = "TEXT"
        )
    }
}
