package com.pantham.nexus.intelligence.bridge

import com.pantham.nexus.core.NexusResult
import com.pantham.nexus.core.actions.NexusAction
import com.pantham.nexus.core.actions.NexusActionRouter
import com.pantham.nexus.intelligence.model.ActionStatus
import com.pantham.nexus.intelligence.model.RiskLevel

class IntelligenceActionBridge(
    private val router: NexusActionRouter
) {

    suspend fun execute(
        action: NexusAction,
        risk: RiskLevel,
        confirmed: Boolean
    ): ActionBridgeResult {

        if (
            risk == RiskLevel.HIGH ||
            risk == RiskLevel.CRITICAL
        ) {
            if (!confirmed) {

                return ActionBridgeResult(
                    status =
                        ActionStatus.WAITING_FOR_CONFIRMATION,
                    message =
                        "Confirmation required."
                )
            }
        }

        return when (
            val result =
                router.execute(
                    action
                )
        ) {

            is NexusResult.Success ->
                ActionBridgeResult(
                    status =
                        ActionStatus.EXECUTED,
                    message =
                        result.message
                )

            is NexusResult.NeedsPermission ->
                ActionBridgeResult(
                    status =
                        ActionStatus.WAITING_FOR_PERMISSION,
                    message =
                        result.explanation
                )

            is NexusResult.NeedsConfirmation ->
                ActionBridgeResult(
                    status =
                        ActionStatus.WAITING_FOR_CONFIRMATION,
                    message =
                        result.actionDescription
                )

            is NexusResult.Blocked ->
                ActionBridgeResult(
                    status =
                        ActionStatus.FAILED,
                    message =
                        result.reason
                )

            is NexusResult.Failed ->
                ActionBridgeResult(
                    status =
                        ActionStatus.FAILED,
                    message =
                        result.reason
                )
        }
    }
}

data class ActionBridgeResult(
    val status: ActionStatus,
    val message: String
)
