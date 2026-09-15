package com.pantham.nexus.decision.engine

import com.pantham.nexus.decision.model.*

class NexusDecisionScoringEngine {

    private val evidenceEngine =
        NexusDecisionEvidenceEngine()

    private val constraintEngine =
        NexusDecisionConstraintEngine()


    fun score(
        request: DecisionRequest,
        options: List<DecisionOption>,
        criteria: List<DecisionCriterion>
    ): List<DecisionScore> {

        val results =
            mutableListOf<DecisionScore>()

        options.forEach { option ->

            criteria.forEach { criterion ->

                val raw =
                    scoreCriterion(
                        request,
                        option,
                        criterion
                    )

                val weighted =
                    raw * criterion.weight

                results += DecisionScore(
                    optionId =
                        option.id,
                    criterionId =
                        criterion.id,
                    rawScore =
                        raw,
                    weightedScore =
                        weighted,
                    explanation =
                        explain(
                            option,
                            criterion,
                            raw
                        )
                )
            }
        }

        return results
    }


    private fun scoreCriterion(
        request: DecisionRequest,
        option: DecisionOption,
        criterion: DecisionCriterion
    ): Float {

        val constraint =
            request.constraints.firstOrNull {
                it.name.equals(
                    criterion.name,
                    ignoreCase = true
                )
            }

        if (constraint != null) {

            return if (
                constraintEngine.satisfies(
                    option,
                    constraint
                )
            ) {
                1f
            } else {
                if (constraint.hardConstraint)
                    0f
                else
                    0.35f
            }
        }

        if (
            criterion.name.equals(
                "Evidence support",
                true
            )
        ) {
            return evidenceEngine
                .evaluateEvidence(
                    option
                )
        }

        if (
            criterion.name.equals(
                "Risk",
                true
            )
        ) {
            return evidenceEngine
                .evaluateEvidence(
                    option
                )
        }

        val matchingAttribute =
            option.attributes[
                criterion.name
            ]

        if (
            !matchingAttribute.isNullOrBlank()
        ) {
            return 0.75f
        }

        return 0.35f
    }


    private fun explain(
        option: DecisionOption,
        criterion: DecisionCriterion,
        score: Float
    ): String {

        return when {
            score >= 0.80f ->
                "${option.name} strongly satisfies ${criterion.name}."

            score >= 0.55f ->
                "${option.name} reasonably satisfies ${criterion.name}."

            score > 0f ->
                "${option.name} only partially satisfies ${criterion.name}."

            else ->
                "${option.name} does not satisfy ${criterion.name}."
        }
    }
}
