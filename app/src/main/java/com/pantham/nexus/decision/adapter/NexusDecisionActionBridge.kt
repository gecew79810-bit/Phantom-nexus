package com.pantham.nexus.decision.adapter

import android.content.Context
import com.example.action.NexusAction
import com.example.action.NexusActionRouter
import com.example.ai.PendingAction
import com.example.security.ToolPermissionMatrix
import com.example.voice.AssistantLanguage
import com.pantham.nexus.decision.model.DecisionAnalysis

sealed interface DecisionActionResult {
    data class RequiresConfirmation(val pendingAction: PendingAction) : DecisionActionResult
    data class ActionRouted(val action: NexusAction) : DecisionActionResult
    data class Rejected(val reason: String) : DecisionActionResult
}

class NexusDecisionActionBridge(
    private val context: Context,
    private val actionRouter: NexusActionRouter? = null
) {

    private val actionKeywords = listOf(
        "choose it", "pick it", "select it", "buy it", "open it", "send it",
        "proceed", "kardo", "kar do", "yeh chuno", "isko chuno", "le lo"
    )

    fun isActionFollowUp(userInput: String): Boolean {
        val lower = userInput.trim().lowercase()
        return actionKeywords.any { lower.contains(it) }
    }

    /**
     * Bridges a user's follow-up decision acceptance into the canonical Nexus Action Pipeline.
     * The Decision Core NEVER executes external tools or actions directly; all actions
     * must pass through NexusActionRouter -> ToolPermissionMatrix -> Confirmation Gate -> ActionAuditLogger.
     */
    suspend fun handleActionFollowUp(
        userInput: String,
        activeAnalysis: DecisionAnalysis?,
        language: AssistantLanguage = AssistantLanguage.ENGLISH,
        onRequestConfirmation: (PendingAction?) -> Unit = {}
    ): DecisionActionResult {
        val recommendation = activeAnalysis?.recommendation
            ?: return DecisionActionResult.Rejected("No active recommendation to act upon.")

        val targetOptionName = recommendation.optionName ?: "Selected Option"

        // Build a canonical NexusAction based on the option/command
        val action: NexusAction = when {
            userInput.contains("open", ignoreCase = true) -> {
                NexusAction.LaunchApp(appName = targetOptionName)
            }
            userInput.contains("call", ignoreCase = true) -> {
                NexusAction.PhoneCall(contactOrNumber = targetOptionName)
            }
            else -> {
                NexusAction.LaunchApp(appName = targetOptionName)
            }
        }

        // Validate with ToolPermissionMatrix
        val toolMatrix = ToolPermissionMatrix.getInstance(context)
        val profile = toolMatrix.getProfileForAction(action)
        val requiresConfirmation = action.requiresConfirmation || toolMatrix.requiresConfirmation(action)

        if (requiresConfirmation) {
            val pending = PendingAction(
                id = action.actionId,
                title = "Execute Choice: $targetOptionName",
                description = "${profile.description}. Do you confirm execution?",
                riskLevel = profile.defaultRiskLevel,
                onConfirm = {
                    onRequestConfirmation(null)
                },
                onCancel = {
                    onRequestConfirmation(null)
                }
            )
            onRequestConfirmation(pending)
            return DecisionActionResult.RequiresConfirmation(pending)
        } else {
            actionRouter?.routeAction(
                action = action,
                bypassConfirmation = false,
                language = language
            )
            return DecisionActionResult.ActionRouted(action)
        }
    }
}
