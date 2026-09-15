package com.pantham.nexus.learning.integration

import com.pantham.nexus.decision.model.DecisionAnalysis
import com.pantham.nexus.learning.model.*

class NexusDecisionLearningBridge {

    fun createChoiceSignal(
        analysis: DecisionAnalysis,
        selectedOptionId: String
    ): LearningSignal {

        val option =
            analysis.request.options
                .firstOrNull {
                    it.id == selectedOptionId
                }

        return LearningSignal(
            id = buildId(),
            type =
                if (analysis.recommendation?.optionId == selectedOptionId) {
                    LearningSignalType.USER_CHOICE
                } else {
                    LearningSignalType.DECISION_OVERRIDE
                },
            key = "decision.option",
            value = option?.name ?: selectedOptionId,
            confidence = 0.90f,
            scope = LearningScope.DECISION_TYPE,
            source = "decision_intelligence"
        )
    }

    fun createRejectionSignal(
        analysis: DecisionAnalysis,
        optionId: String
    ): LearningSignal {

        val option =
            analysis.request.options
                .firstOrNull {
                    it.id == optionId
                }

        return LearningSignal(
            id = buildId(),
            type = LearningSignalType.USER_REJECTION,
            key = "decision.option",
            value = option?.name ?: optionId,
            confidence = 0.85f,
            scope = LearningScope.DECISION_TYPE,
            source = "decision_intelligence"
        )
    }

    fun createCorrectionSignal(
        key: String,
        value: String
    ): LearningSignal {

        return LearningSignal(
            id = buildId(),
            type = LearningSignalType.USER_CORRECTION,
            key = key,
            value = value,
            confidence = 0.98f,
            scope = LearningScope.USER_PREFERENCE,
            source = "user_correction"
        )
    }

    fun createPreferenceSignal(
        key: String,
        value: String
    ): LearningSignal {

        return LearningSignal(
            id = buildId(),
            type = LearningSignalType.EXPLICIT_PREFERENCE,
            key = key,
            value = value,
            confidence = 1f,
            scope = LearningScope.USER_PREFERENCE,
            source = "user_statement"
        )
    }

    private fun buildId(): String =
        "learning_" +
            System.currentTimeMillis() +
            "_" +
            (0..999999).random()
}
