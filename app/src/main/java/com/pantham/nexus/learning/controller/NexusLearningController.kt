package com.pantham.nexus.learning.controller

import com.pantham.nexus.learning.integration.NexusDecisionLearningBridge
import com.pantham.nexus.learning.model.*
import com.pantham.nexus.learning.runtime.NexusAdaptiveLearningRuntime

class NexusLearningController(
    private val runtime: NexusAdaptiveLearningRuntime = NexusAdaptiveLearningRuntime.getInstance(),
    private val decisionBridge: NexusDecisionLearningBridge = NexusDecisionLearningBridge()
) {

    suspend fun recordDecisionChoice(
        analysis: com.pantham.nexus.decision.model.DecisionAnalysis,
        selectedOptionId: String
    ): LearningUpdateResult? {

        val signal =
            decisionBridge.createChoiceSignal(
                analysis,
                selectedOptionId
            )

        return runtime.recordSignal(signal)
    }

    suspend fun recordDecisionOutcome(
        decisionId: String,
        optionId: String,
        status: OutcomeStatus
    ): LearningUpdateResult? {

        val signal =
            LearningSignal(
                id = "outcome_" + System.currentTimeMillis(),
                type =
                    when (status) {
                        OutcomeStatus.SUCCESS ->
                            LearningSignalType.OUTCOME_SUCCESS
                        OutcomeStatus.FAILURE ->
                            LearningSignalType.OUTCOME_FAILURE
                        OutcomeStatus.PARTIAL ->
                            LearningSignalType.OUTCOME_PARTIAL
                        OutcomeStatus.CANCELLED ->
                            LearningSignalType.DECISION_OVERRIDE
                        OutcomeStatus.UNKNOWN ->
                            LearningSignalType.IMPLICIT_PREFERENCE
                    },
                key = "decision.$optionId",
                value = status.name,
                confidence = 0.85f,
                scope = LearningScope.DECISION_TYPE,
                source = "decision_outcome"
            )

        return runtime.recordSignal(
            signal = signal,
            outcome =
                DecisionOutcome(
                    decisionId = decisionId,
                    status = status,
                    selectedOptionId = optionId
                )
        )
    }

    suspend fun recordExplicitPreference(
        key: String,
        value: String
    ): LearningUpdateResult? {

        val signal =
            decisionBridge.createPreferenceSignal(
                key,
                value
            )

        return runtime.recordSignal(signal)
    }

    suspend fun recordCorrection(
        key: String,
        value: String
    ): LearningUpdateResult? {

        val signal =
            decisionBridge.createCorrectionSignal(
                key,
                value
            )

        return runtime.recordSignal(signal)
    }
}
