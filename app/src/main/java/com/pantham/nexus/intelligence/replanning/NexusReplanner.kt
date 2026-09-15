package com.pantham.nexus.intelligence.replanning

import com.pantham.nexus.intelligence.model.AgentResult
import com.pantham.nexus.intelligence.model.TaskPlan
import com.pantham.nexus.intelligence.planning.NexusGoalPlanner

class NexusReplanner(
    private val planner: NexusGoalPlanner
) {

    fun shouldReplan(
        result: AgentResult
    ): Boolean {

        return result.shouldReplan
    }

    fun createFallbackGoal(
        originalGoal: String,
        result: AgentResult
    ): String {

        val reason =
            result.failureReason
                ?: "unknown failure"

        return """
            Original goal:
            $originalGoal

            Previous execution failed because:
            $reason

            Find a safe alternative path to achieve the same goal.
        """.trimIndent()
    }
}
