package com.pantham.nexus.decision.engine

import com.pantham.nexus.decision.model.*

class NexusDecisionConstraintEngine {

    fun satisfies(
        option: DecisionOption,
        constraint: DecisionConstraint
    ): Boolean {

        val value =
            option.attributes[
                constraint.name
            ] ?: return !constraint.hardConstraint

        if (value.equals(
                constraint.value,
                ignoreCase = true
            )
        ) {
            return true
        }

        return !constraint.hardConstraint
    }


    fun violationReason(
        option: DecisionOption,
        constraint: DecisionConstraint
    ): String? {

        val value =
            option.attributes[
                constraint.name
            ] ?: return if (
                constraint.hardConstraint
            ) {
                "Required information '${constraint.name}' is missing."
            } else {
                null
            }

        if (
            value.equals(
                constraint.value,
                ignoreCase = true
            )
        ) {
            return null
        }

        return if (
            constraint.hardConstraint
        ) {
            "'${constraint.name}' does not satisfy required value '${constraint.value}'."
        } else {
            "Preference '${constraint.name}' is not fully satisfied."
        }
    }
}
