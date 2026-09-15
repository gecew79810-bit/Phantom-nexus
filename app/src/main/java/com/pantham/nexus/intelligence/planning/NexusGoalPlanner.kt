package com.pantham.nexus.intelligence.planning

import com.pantham.nexus.intelligence.model.IntentResult
import com.pantham.nexus.intelligence.model.IntentType
import com.pantham.nexus.intelligence.model.TaskNode
import com.pantham.nexus.intelligence.model.TaskPlan

class NexusGoalPlanner {

    fun buildPlan(
        goal: String,
        primaryIntent: IntentResult
    ): TaskPlan {

        val normalized =
            goal.lowercase()

        val nodes =
            when {

                containsResearchAndGeneration(
                    normalized
                ) -> {
                    buildResearchGenerationPlan()
                }

                containsMeetingWorkflow(
                    normalized
                ) -> {
                    buildMeetingWorkflow()
                }

                containsBriefingWorkflow(
                    normalized
                ) -> {
                    buildBriefingWorkflow()
                }

                else -> {
                    listOf(
                        TaskNode(
                            name = "Execute request",
                            intent = primaryIntent.type,
                            risk = primaryIntent.risk
                        )
                    )
                }
            }

        return TaskPlan(
            goal = goal,
            nodes = nodes
        )
    }

    private fun buildResearchGenerationPlan():
            List<TaskNode> {

        val research =
            TaskNode(
                name = "Research",
                intent = IntentType.RESEARCH,
                parallelGroup = 1
            )

        val compare =
            TaskNode(
                name = "Compare findings",
                intent = IntentType.RESEARCH,
                dependencies = setOf(
                    research.id
                )
            )

        val pdf =
            TaskNode(
                name = "Generate PDF",
                intent = IntentType.DOCUMENT_GENERATION,
                dependencies = setOf(
                    compare.id
                )
            )

        val presentation =
            TaskNode(
                name = "Generate Presentation",
                intent = IntentType.PRESENTATION_GENERATION,
                dependencies = setOf(
                    compare.id
                )
            )

        return listOf(
            research,
            compare,
            pdf,
            presentation
        )
    }

    private fun buildMeetingWorkflow():
            List<TaskNode> {

        val lookup =
            TaskNode(
                name = "Find meeting",
                intent = IntentType.CALENDAR,
                parallelGroup = 1
            )

        val presentation =
            TaskNode(
                name = "Prepare presentation",
                intent =
                    IntentType.PRESENTATION_GENERATION,
                parallelGroup = 1
            )

        val reminder =
            TaskNode(
                name = "Schedule reminder",
                intent = IntentType.REMINDER,
                dependencies = setOf(
                    lookup.id
                ),
                condition =
                    com.pantham.nexus.intelligence.model
                        .TaskCondition.NodeProducedValue(
                            nodeId = lookup.id,
                            key = "meetingFound",
                            expected = "true"
                        )
            )

        return listOf(
            lookup,
            presentation,
            reminder
        )
    }

    private fun buildBriefingWorkflow():
            List<TaskNode> {

        val weather =
            TaskNode(
                name = "Get weather",
                intent = IntentType.WEB_SEARCH,
                parallelGroup = 1
            )

        val calendar =
            TaskNode(
                name = "Get calendar",
                intent = IntentType.CALENDAR,
                parallelGroup = 1
            )

        val notifications =
            TaskNode(
                name = "Get notifications",
                intent = IntentType.READ_NOTIFICATIONS,
                parallelGroup = 1
            )

        val briefing =
            TaskNode(
                name = "Generate briefing",
                intent = IntentType.GENERAL_CHAT,
                dependencies = setOf(
                    weather.id,
                    calendar.id,
                    notifications.id
                )
            )

        return listOf(
            weather,
            calendar,
            notifications,
            briefing
        )
    }

    private fun containsResearchAndGeneration(
        value: String
    ): Boolean {

        return (
            value.contains("research") &&
            (
                value.contains("pdf") ||
                value.contains("presentation") ||
                value.contains("ppt")
            )
        )
    }

    private fun containsMeetingWorkflow(
        value: String
    ): Boolean {

        return value.contains("meeting") &&
                (
                    value.contains("presentation") ||
                    value.contains("reminder")
                )
    }

    private fun containsBriefingWorkflow(
        value: String
    ): Boolean {

        return value.contains("briefing") ||
                (
                    value.contains("weather") &&
                    value.contains("calendar")
                )
    }
}
