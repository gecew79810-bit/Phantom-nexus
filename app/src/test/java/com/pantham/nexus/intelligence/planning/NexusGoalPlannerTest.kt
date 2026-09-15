package com.pantham.nexus.intelligence.planning

import com.pantham.nexus.intelligence.model.ConfidenceLevel
import com.pantham.nexus.intelligence.model.IntentResult
import com.pantham.nexus.intelligence.model.IntentType
import com.pantham.nexus.intelligence.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusGoalPlannerTest {

    private val planner = NexusGoalPlanner()

    @Test
    fun testResearchGenerationPlanCreation() {
        val intent = IntentResult(
            type = IntentType.RESEARCH,
            confidence = 0.9,
            confidenceLevel = ConfidenceLevel.VERY_HIGH,
            risk = RiskLevel.LOW
        )

        val plan = planner.buildPlan("Research electric vehicles and generate pdf report", intent)

        assertEquals("Research electric vehicles and generate pdf report", plan.goal)
        assertEquals(4, plan.nodes.size)

        val research = plan.nodes[0]
        val compare = plan.nodes[1]
        val pdf = plan.nodes[2]
        val presentation = plan.nodes[3]

        assertEquals("Research", research.name)
        assertEquals(IntentType.RESEARCH, research.intent)
        assertEquals(1, research.parallelGroup)

        assertEquals("Compare findings", compare.name)
        assertTrue(compare.dependencies.contains(research.id))

        assertEquals("Generate PDF", pdf.name)
        assertEquals(IntentType.DOCUMENT_GENERATION, pdf.intent)
        assertTrue(pdf.dependencies.contains(compare.id))

        assertEquals("Generate Presentation", presentation.name)
        assertEquals(IntentType.PRESENTATION_GENERATION, presentation.intent)
        assertTrue(presentation.dependencies.contains(compare.id))
    }

    @Test
    fun testMeetingWorkflowCreation() {
        val intent = IntentResult(
            type = IntentType.CALENDAR,
            confidence = 0.85,
            confidenceLevel = ConfidenceLevel.HIGH,
            risk = RiskLevel.MEDIUM
        )

        val plan = planner.buildPlan("Find my meeting and schedule reminder", intent)

        assertEquals(3, plan.nodes.size)
        val lookup = plan.nodes[0]
        val presentation = plan.nodes[1]
        val reminder = plan.nodes[2]

        assertEquals("Find meeting", lookup.name)
        assertEquals(IntentType.CALENDAR, lookup.intent)

        assertEquals("Prepare presentation", presentation.name)
        assertEquals(IntentType.PRESENTATION_GENERATION, presentation.intent)

        assertEquals("Schedule reminder", reminder.name)
        assertTrue(reminder.dependencies.contains(lookup.id))
        assertNotNull(reminder.condition)
    }

    @Test
    fun testBriefingWorkflowCreation() {
        val intent = IntentResult(
            type = IntentType.GENERAL_CHAT,
            confidence = 0.8,
            confidenceLevel = ConfidenceLevel.HIGH,
            risk = RiskLevel.LOW
        )

        val plan = planner.buildPlan("Give me my morning briefing", intent)

        assertEquals(4, plan.nodes.size)
        val weather = plan.nodes[0]
        val calendar = plan.nodes[1]
        val notifications = plan.nodes[2]
        val briefing = plan.nodes[3]

        assertEquals("Get weather", weather.name)
        assertEquals(IntentType.WEB_SEARCH, weather.intent)

        assertEquals("Get calendar", calendar.name)
        assertEquals(IntentType.CALENDAR, calendar.intent)

        assertEquals("Get notifications", notifications.name)
        assertEquals(IntentType.READ_NOTIFICATIONS, notifications.intent)

        assertEquals("Generate briefing", briefing.name)
        assertTrue(briefing.dependencies.contains(weather.id))
        assertTrue(briefing.dependencies.contains(calendar.id))
        assertTrue(briefing.dependencies.contains(notifications.id))
    }

    @Test
    fun testFallbackSingleExecutionPlan() {
        val intent = IntentResult(
            type = IntentType.OPEN_APP,
            confidence = 0.95,
            confidenceLevel = ConfidenceLevel.VERY_HIGH,
            risk = RiskLevel.LOW
        )

        val plan = planner.buildPlan("open youtube", intent)

        assertEquals(1, plan.nodes.size)
        val node = plan.nodes.first()
        assertEquals("Execute request", node.name)
        assertEquals(IntentType.OPEN_APP, node.intent)
        assertEquals(RiskLevel.LOW, node.risk)
    }
}
