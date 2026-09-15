package com.pantham.nexus.learning.integration

import com.example.data.audit.ActionAuditRecord
import com.pantham.nexus.learning.controller.NexusLearningController
import com.pantham.nexus.learning.model.OutcomeStatus

class NexusLearningOutcomeBridge(
    private val controller: NexusLearningController = NexusLearningController()
) {

    suspend fun onTaskFinished(
        taskId: String,
        actionName: String,
        success: Boolean,
        failureReason: String? = null
    ) {
        val status = if (success) OutcomeStatus.SUCCESS else OutcomeStatus.FAILURE
        controller.recordDecisionOutcome(
            decisionId = taskId,
            optionId = actionName,
            status = status
        )
    }

    suspend fun onAuditRecord(record: ActionAuditRecord) {
        // Strict guardrail: Do not infer success merely because an action started.
        // "Action dispatched" != "Outcome successful" - use verified result only.
        val status = when {
            record.result.equals("SUCCESS", ignoreCase = true) -> OutcomeStatus.SUCCESS
            record.result.equals("FAILURE", ignoreCase = true) || record.failureReason != null -> OutcomeStatus.FAILURE
            record.result.equals("CANCELLED", ignoreCase = true) -> OutcomeStatus.CANCELLED
            record.result.equals("PARTIAL", ignoreCase = true) -> OutcomeStatus.PARTIAL
            else -> OutcomeStatus.UNKNOWN
        }

        if (status != OutcomeStatus.UNKNOWN) {
            controller.recordDecisionOutcome(
                decisionId = record.actionId,
                optionId = record.selectedTool,
                status = status
            )
        }
    }
}
