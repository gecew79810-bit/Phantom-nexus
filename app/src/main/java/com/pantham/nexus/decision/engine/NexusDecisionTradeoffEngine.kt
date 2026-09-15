package com.pantham.nexus.decision.engine

import com.pantham.nexus.decision.model.*

class NexusDecisionTradeoffEngine {

    fun detect(
        scores: List<DecisionScore>,
        criteria: List<DecisionCriterion>,
        options: List<DecisionOption>
    ): List<DecisionTradeoff> {

        val results =
            mutableListOf<DecisionTradeoff>()

        if (options.size < 2) {
            return results
        }

        criteria.forEach { criterion ->

            val criterionScores =
                scores.filter {
                    it.criterionId ==
                        criterion.id
                }

            if (criterionScores.size < 2) {
                return@forEach
            }

            val best =
                criterionScores
                    .maxByOrNull {
                        it.rawScore
                    } ?: return@forEach

            val worst =
                criterionScores
                    .minByOrNull {
                        it.rawScore
                    } ?: return@forEach

            if (
                best.optionId != worst.optionId &&
                best.rawScore -
                worst.rawScore >= 0.20f
            ) {

                val bestName =
                    options.firstOrNull {
                        it.id == best.optionId
                    }?.name
                        ?: best.optionId

                val worstName =
                    options.firstOrNull {
                        it.id == worst.optionId
                    }?.name
                        ?: worst.optionId

                results += DecisionTradeoff(
                    criterion =
                        criterion.name,
                    preferredOptionId =
                        best.optionId,
                    sacrificedOptionId =
                        worst.optionId,
                    explanation =
                        "$bestName performs better on ${criterion.name}, while $worstName gives up ground on this criterion."
                )
            }
        }

        return results
    }
}
