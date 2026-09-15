package com.pantham.nexus.decision.engine

import com.pantham.nexus.decision.model.*

class NexusDecisionMissingInfoEngine {

    fun detect(
        request: DecisionRequest,
        options: List<DecisionOption>,
        criteria: List<DecisionCriterion>
    ): List<MissingDecisionInformation> {

        val result =
            mutableListOf<MissingDecisionInformation>()

        criteria.forEach { criterion ->

            val missingCount =
                options.count { option ->
                    option.attributes[
                        criterion.name
                    ].isNullOrBlank()
                }

            if (missingCount > 0) {

                val ratio =
                    missingCount.toFloat() /
                        options.size.coerceAtLeast(1)

                result +=
                    MissingDecisionInformation(
                        field =
                            criterion.name,
                        reason =
                            "$missingCount option(s) lack information for this criterion.",
                        importance =
                            criterion.weight,
                        blocking =
                            criterion.required &&
                                ratio >= 0.5f
                    )
            }
        }

        if (
            options.size < 2
        ) {

            result +=
                MissingDecisionInformation(
                    field =
                        "comparison options",
                    reason =
                        "At least two options are needed for a meaningful comparison.",
                    importance = 1f,
                    blocking = true
                )
        }

        if (
            request.evidence.isEmpty() &&
            options.all {
                it.evidence.isEmpty()
            }
        ) {

            result +=
                MissingDecisionInformation(
                    field =
                        "supporting evidence",
                    reason =
                        "No reliable supporting evidence was supplied.",
                    importance = 0.80f,
                    blocking = false
                )
        }

        return result
    }
}
