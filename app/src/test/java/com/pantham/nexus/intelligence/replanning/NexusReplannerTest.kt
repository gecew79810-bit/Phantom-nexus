package com.pantham.nexus.intelligence.replanning

import com.pantham.nexus.intelligence.model.AgentResult
import com.pantham.nexus.intelligence.planning.NexusGoalPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusReplannerTest {

    private val planner = NexusGoalPlanner()
    private val replanner = NexusReplanner(planner)

    @Test
    fun testShouldReplanDetection() {
        val resultNeedingReplan = AgentResult(
            success = false,
            shouldReplan = true,
            failureReason = "SERVICE_TIMEOUT"
        )
        assertTrue(replanner.shouldReplan(resultNeedingReplan))

        val resultNoReplan = AgentResult(
            success = false,
            shouldReplan = false,
            failureReason = "USER_DENIED"
        )
        assertFalse(replanner.shouldReplan(resultNoReplan))
    }

    @Test
    fun testCreateFallbackGoalWithReason() {
        val result = AgentResult(
            success = false,
            shouldReplan = true,
            failureReason = "Network socket timeout on remote endpoint"
        )

        val fallback = replanner.createFallbackGoal("Order cab to airport", result)

        assertTrue(fallback.contains("Original goal:\nOrder cab to airport"))
        assertTrue(fallback.contains("Network socket timeout on remote endpoint"))
        assertTrue(fallback.contains("Find a safe alternative path to achieve the same goal."))
    }

    @Test
    fun testCreateFallbackGoalWithoutReasonDefaultsToUnknown() {
        val result = AgentResult(
            success = false,
            shouldReplan = true,
            failureReason = null
        )

        val fallback = replanner.createFallbackGoal("Play music", result)

        assertTrue(fallback.contains("unknown failure"))
    }
}
