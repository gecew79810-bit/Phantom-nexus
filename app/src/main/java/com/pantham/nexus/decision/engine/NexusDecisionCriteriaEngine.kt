package com.pantham.nexus.decision.engine

import com.pantham.nexus.decision.model.*

class NexusDecisionCriteriaEngine {

    fun buildCriteria(
        request: DecisionRequest
    ): List<DecisionCriterion> {

        if (request.criteria.isNotEmpty()) {
            return normalizeWeights(
                request.criteria
            )
        }

        val criteria =
            mutableListOf<DecisionCriterion>()

        request.constraints.forEachIndexed { index, constraint ->

            criteria += DecisionCriterion(
                id = "constraint_$index",
                name = constraint.name,
                weight = constraint.importance
                    .coerceIn(0.01f, 1f),
                higherIsBetter = true,
                required = constraint.hardConstraint
            )
        }

        if (criteria.isEmpty()) {

            criteria += DecisionCriterion(
                id = "evidence",
                name = "Evidence support",
                weight = 0.5f
            )

            criteria += DecisionCriterion(
                id = "risk",
                name = "Risk",
                weight = 0.5f,
                higherIsBetter = false
            )
        }

        return normalizeWeights(criteria)
    }


    private fun normalizeWeights(
        criteria: List<DecisionCriterion>
    ): List<DecisionCriterion> {

        val total =
            criteria.sumOf {
                it.weight.toDouble()
            }.toFloat()

        if (total <= 0f) {
            return criteria
        }

        return criteria.map {
            it.copy(
                weight =
                    it.weight / total
            )
        }
    }
}
