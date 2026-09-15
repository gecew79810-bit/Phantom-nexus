package com.pantham.nexus.decision.engine

import com.pantham.nexus.decision.model.*

class NexusDecisionRiskEngine {

    fun assess(
        request: DecisionRequest,
        option: DecisionOption
    ): List<DecisionRiskItem> {

        val risks =
            mutableListOf<DecisionRiskItem>()

        if (option.evidence.isEmpty()) {

            risks += DecisionRiskItem(
                optionId = option.id,
                description =
                    "Limited supporting evidence.",
                probability = 0.55f,
                impact = 0.45f,
                mitigation =
                    "Gather more reliable evidence before making a high-stakes decision."
            )
        }

        request.constraints.forEach { constraint ->

            val violation =
                NexusDecisionConstraintEngine()
                    .violationReason(
                        option,
                        constraint
                    )

            if (violation != null) {

                risks += DecisionRiskItem(
                    optionId = option.id,
                    description = violation,
                    probability =
                        if (constraint.hardConstraint)
                            0.90f
                        else
                            0.45f,
                    impact =
                        if (constraint.hardConstraint)
                            0.95f
                        else
                            0.45f
                )
            }
        }

        return risks
    }
}
