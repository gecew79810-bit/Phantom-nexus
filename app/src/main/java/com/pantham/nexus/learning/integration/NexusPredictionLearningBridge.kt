package com.pantham.nexus.learning.integration

import com.pantham.nexus.learning.model.LearningScope
import com.pantham.nexus.learning.model.LearningSignal
import com.pantham.nexus.learning.model.LearningSignalType

class NexusPredictionLearningBridge {

    fun createOutcomeSignal(
        predictionId: String,
        success: Boolean
    ): LearningSignal {

        return LearningSignal(
            id =
                "prediction_feedback_" +
                    System.currentTimeMillis(),
            type =
                if (success) {
                    LearningSignalType.OUTCOME_SUCCESS
                } else {
                    LearningSignalType.OUTCOME_FAILURE
                },
            key =
                "prediction.$predictionId",
            value =
                success.toString(),
            confidence =
                0.85f,
            scope =
                LearningScope.GENERAL_STRATEGY,
            source =
                "prediction_feedback"
        )
    }
}
