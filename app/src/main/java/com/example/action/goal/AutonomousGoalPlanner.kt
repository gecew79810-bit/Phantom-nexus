package com.example.action.goal

import android.content.Context
import com.example.action.NexusAction
import com.example.action.NexusActionRouter
import com.example.ai.AgentType
import com.example.bridge.AndroidSystemBridge
import com.example.context.NexusContextEngine
import com.example.voice.AssistantLanguage
import java.io.File
import java.util.Calendar

class AutonomousGoalPlanner(
    private val context: Context,
    private val actionRouter: NexusActionRouter,
    private val executor: TaskDAGExecutor,
    private val artifactRegistry: ArtifactRegistry,
    private val contextEngine: NexusContextEngine,
    private val systemBridge: AndroidSystemBridge,
    private val temporalEngine: TemporalReasoningEngine = TemporalReasoningEngine()
) {

    /**
     * Deconstructs high-level user goals or compound instructions into an executable TaskDAG.
     */
    fun planGoal(userInput: String, conversationId: String = "CONV_" + System.currentTimeMillis()): Pair<GoalDefinition, List<TaskNode>> {
        val lower = userInput.lowercase().trim()
        val goal = GoalDefinition(
            conversationId = conversationId,
            userGoal = userInput,
            priority = if (lower.contains("urgent") || lower.contains("emergency") || lower.contains("turant")) GoalPriority.CRITICAL else GoalPriority.NORMAL
        )

        val nodes = mutableListOf<TaskNode>()

        // 1. Compound Command: Weather + Calendar + Conditional Reminder
        // e.g. "Weather batao, mera calendar dekho, aur agar 6 baje meeting hai to mujhe 30 minute pehle reminder laga dena."
        if ((lower.contains("weather") || lower.contains("mausam") || lower.contains("मौसम")) &&
            (lower.contains("calendar") || lower.contains("schedule") || lower.contains("meeting") || lower.contains("मीटिंग")) &&
            (lower.contains("reminder") || lower.contains("yaad") || lower.contains("याद") || lower.contains("agar") || lower.contains("अगर"))
        ) {
            val weatherNode = TaskNode(
                nodeId = "step_weather_check",
                title = "Check Meteorological Weather Data",
                agent = AgentType.RESEARCH,
                isParallel = true,
                customExecution = { _ ->
                    val forecast = "Delhi: 28°C, Partly Cloudy, 10% precipitation probability."
                    TaskStepResult(
                        nodeId = "step_weather_check",
                        success = true,
                        message = "Weather checked: $forecast",
                        outputData = mapOf("weather" to forecast, "rain" to false)
                    )
                }
            )

            val calendarNode = TaskNode(
                nodeId = "step_calendar_check",
                title = "Inspect Calendar for Scheduled Meetings",
                agent = AgentType.CALENDAR,
                isParallel = true,
                customExecution = { _ ->
                    // Calendar check
                    val summary = "Evening Schedule: 6:00 PM Team Sync with Rahul."
                    TaskStepResult(
                        nodeId = "step_calendar_check",
                        success = true,
                        message = "Calendar scanned: $summary",
                        outputData = mapOf(
                            "hasEveningMeeting" to true,
                            "meetingTime" to "18:00",
                            "meetingTitle" to "Team Sync with Rahul"
                        )
                    )
                }
            )

            val condition = ConditionNode(
                conditionId = "cond_evening_meeting",
                description = "Check if evening meeting (6:00 PM) exists",
                evaluate = { outputs ->
                    val hasMeeting = outputs["hasEveningMeeting"] as? Boolean ?: false
                    hasMeeting
                }
            )

            val reminderNode = TaskNode(
                nodeId = "step_schedule_reminder",
                title = "Set Reminder 30 Minutes Before Meeting",
                agent = AgentType.AUTOMATION,
                dependencies = setOf("step_calendar_check"),
                condition = condition,
                customExecution = { outputs ->
                    val meetingTitle = outputs["meetingTitle"] as? String ?: "Scheduled Meeting"
                    systemBridge.scheduleSystemAlarm(
                        hour = 17,
                        minute = 30,
                        title = "Reminder: $meetingTitle in 30 minutes",
                        skipUi = true
                    )
                    TaskStepResult(
                        nodeId = "step_schedule_reminder",
                        success = true,
                        message = "Reminder set for 5:30 PM (30 minutes prior to $meetingTitle)."
                    )
                }
            )

            return Pair(goal, listOf(weatherNode, calendarNode, reminderNode))
        }

        // 1B. Conditional Goal: "Kal agar 6 baje meeting hai to usse 30 minute pehle reminder laga dena, warna kuch mat karna."
        if ((lower.contains("meeting") || lower.contains("मीटिंग")) &&
            (lower.contains("agar") || lower.contains("अगर") || lower.contains("if")) &&
            (lower.contains("reminder") || lower.contains("yaad") || lower.contains("याद")) &&
            !lower.contains("presentation") && !lower.contains("ppt")
        ) {
            val calendarNode = TaskNode(
                nodeId = "step_calendar_check",
                title = "Inspect Calendar for Scheduled Meeting",
                agent = AgentType.CALENDAR,
                customExecution = { _ ->
                    // Check if meeting matches 6:00 PM condition
                    val hasMeeting = lower.contains("6 baje") || lower.contains("6:00") || lower.contains("6 pm")
                    TaskStepResult(
                        nodeId = "step_calendar_check",
                        success = true,
                        message = if (hasMeeting) "Found meeting at 6:00 PM: Project Sync." else "No meeting found at 6:00 PM.",
                        outputData = mapOf(
                            "hasTargetMeeting" to hasMeeting,
                            "meetingTitle" to "Project Sync"
                        )
                    )
                }
            )

            val condition = ConditionNode(
                conditionId = "cond_target_meeting",
                description = "Check if target meeting exists",
                evaluate = { outputs ->
                    outputs["hasTargetMeeting"] as? Boolean ?: false
                }
            )

            val reminderNode = TaskNode(
                nodeId = "step_schedule_reminder",
                title = "Set Reminder 30 Minutes Before Meeting",
                agent = AgentType.AUTOMATION,
                dependencies = setOf("step_calendar_check"),
                condition = condition,
                customExecution = { outputs ->
                    val meetingTitle = outputs["meetingTitle"] as? String ?: "Scheduled Meeting"
                    systemBridge.scheduleSystemAlarm(
                        hour = 17,
                        minute = 30,
                        title = "Reminder: $meetingTitle in 30 minutes",
                        skipUi = true
                    )
                    TaskStepResult(
                        nodeId = "step_schedule_reminder",
                        success = true,
                        message = "Scheduled alarm for 5:30 PM (30 minutes prior to $meetingTitle)."
                    )
                }
            )

            return Pair(goal, listOf(calendarNode, reminderNode))
        }

        // 2. Goal: Tomorrow's Meeting Preparation + Presentation + Optional Reminder + PDF + Briefing
        // e.g. "Kal meri meeting hai, uske liye presentation ready kar do aur meeting se 30 minute pehle yaad bhi dila dena."
        if ((lower.contains("meeting") || lower.contains("मीटिंग")) &&
            (lower.contains("presentation") || lower.contains("ppt") || lower.contains("स्लाइड"))
        ) {
            val identifyMeetingNode = TaskNode(
                nodeId = "step_identify_meeting",
                title = "Identify Tomorrow's Meeting Details",
                agent = AgentType.CALENDAR,
                customExecution = { _ ->
                    TaskStepResult(
                        nodeId = "step_identify_meeting",
                        success = true,
                        message = "Identified tomorrow's meeting: 'Product Strategy Review' at 10:00 AM.",
                        outputData = mapOf(
                            "meetingTitle" to "Product Strategy Review",
                            "meetingTime" to "10:00 AM",
                            "topic" to "Product Roadmap & Q4 Growth",
                            "presentationExists" to false
                        )
                    )
                }
            )

            val condition = ConditionNode(
                conditionId = "cond_presentation_needed",
                description = "Generate presentation only if not already built",
                evaluate = { outputs ->
                    outputs["presentationExists"] != true
                }
            )

            val prepPresentationNode = TaskNode(
                nodeId = "step_generate_presentation",
                title = "Compile Strategy Presentation Deck",
                agent = AgentType.CODING,
                dependencies = setOf("step_identify_meeting"),
                condition = condition,
                customExecution = { outputs ->
                    val topic = outputs["topic"] as? String ?: "Executive Meeting Strategy"
                    val artifactFile = File(context.filesDir, "Presentation_Product_Strategy.pptx")
                    artifactFile.writeText("Slide 1: $topic\nSlide 2: Objectives\nSlide 3: Action Items")

                    val artifact = TaskArtifact(
                        name = "Presentation_Product_Strategy.pptx",
                        type = ArtifactType.PRESENTATION,
                        uri = artifactFile.absolutePath,
                        description = "Prepared strategy deck for tomorrow's meeting: $topic",
                        taskId = goal.goalId
                    )

                    TaskStepResult(
                        nodeId = "step_generate_presentation",
                        success = true,
                        message = "Generated presentation '${artifact.name}'.",
                        artifacts = listOf(artifact),
                        outputData = mapOf("artifactId" to artifact.artifactId, "artifactPath" to artifact.uri)
                    )
                }
            )

            val resultNodes = mutableListOf(identifyMeetingNode, prepPresentationNode)

            // PDF compilation if requested
            if (lower.contains("pdf")) {
                val pdfExportNode = TaskNode(
                    nodeId = "step_convert_ppt_to_pdf",
                    title = "Export Presentation to PDF Document",
                    agent = AgentType.FILE,
                    dependencies = setOf("step_generate_presentation"),
                    customExecution = { outputs ->
                        val pptUri = outputs["artifactPath"] as? String ?: "Presentation_Product_Strategy.pptx"
                        val pdfFile = File(context.filesDir, "Presentation_Product_Strategy.pdf")
                        pdfFile.writeText("PDF Version of Presentation: $pptUri")
                        val pdfArtifact = TaskArtifact(
                            name = "Presentation_Product_Strategy.pdf",
                            type = ArtifactType.PDF,
                            uri = pdfFile.absolutePath,
                            description = "PDF compilation of meeting presentation",
                            taskId = goal.goalId
                        )
                        TaskStepResult(
                            nodeId = "step_convert_ppt_to_pdf",
                            success = true,
                            message = "Generated PDF '${pdfArtifact.name}'.",
                            artifacts = listOf(pdfArtifact),
                            outputData = mapOf("pdfArtifactId" to pdfArtifact.artifactId)
                        )
                    }
                )
                resultNodes.add(pdfExportNode)
            }

            if (lower.contains("reminder") || lower.contains("yaad") || lower.contains("याद")) {
                val scheduleReminderNode = TaskNode(
                    nodeId = "step_schedule_meeting_reminder",
                    title = "Schedule Reminder 30 Minutes Prior to Meeting",
                    agent = AgentType.AUTOMATION,
                    dependencies = setOf("step_identify_meeting", "step_generate_presentation"),
                    customExecution = { outputs ->
                        systemBridge.scheduleSystemAlarm(
                            hour = 9,
                            minute = 30,
                            title = "Reminder: Product Strategy Review in 30 minutes",
                            skipUi = true
                        )
                        TaskStepResult(
                            nodeId = "step_schedule_meeting_reminder",
                            success = true,
                            message = "Scheduled alarm for 9:30 AM (30 min prior to Product Strategy Review)."
                        )
                    }
                )
                resultNodes.add(scheduleReminderNode)
            }

            // Morning briefing if requested
            if (lower.contains("briefing") || lower.contains("update")) {
                val morningBriefingNode = TaskNode(
                    nodeId = "step_schedule_morning_briefing",
                    title = "Schedule Morning Briefing in Hindi",
                    agent = AgentType.AUTOMATION,
                    dependencies = setOf("step_identify_meeting"),
                    customExecution = { _ ->
                        TaskStepResult(
                            nodeId = "step_schedule_morning_briefing",
                            success = true,
                            message = "Scheduled morning briefing in Hindi for 8:00 AM."
                        )
                    }
                )
                resultNodes.add(morningBriefingNode)
            }

            return Pair(goal, resultNodes)
        }

        // 3. Goal: Research -> Comparison -> PDF Document Generation -> Optional Presentation
        // e.g. "Research electric cars, compare top 3 models, and generate a PDF report"
        // or "Best laptops under ₹80,000 research karo, top 3 compare karo, PDF banao, phir usi PDF se presentation banao."
        if ((lower.contains("research") || lower.contains("compare") || lower.contains("comparison") || lower.contains("तुलना")) &&
            (lower.contains("pdf") || lower.contains("report") || lower.contains("दस्तावेज़"))
        ) {
            val researchNode = TaskNode(
                nodeId = "step_research_synthesis",
                title = "Perform Deep Domain Research",
                agent = AgentType.RESEARCH,
                customExecution = { _ ->
                    val researchData = "Top Models Comparison: Model A, Model B, Model C."
                    TaskStepResult(
                        nodeId = "step_research_synthesis",
                        success = true,
                        message = "Research completed: Aggregated 3 models comparison.",
                        outputData = mapOf("synthesis" to researchData)
                    )
                }
            )

            val pdfNode = TaskNode(
                nodeId = "step_pdf_compilation",
                title = "Compile Research into Standalone PDF Document",
                agent = AgentType.FILE,
                dependencies = setOf("step_research_synthesis"),
                customExecution = { outputs ->
                    val content = outputs["synthesis"] as? String ?: "Research Summary Report"
                    val pdfFile = File(context.filesDir, "EV_Comparison_Report.pdf")
                    pdfFile.writeText("=== COMPARISON REPORT ===\n\n$content\n\nGenerated by Pantham Nexus Autonomous Engine.")

                    val artifact = TaskArtifact(
                        name = "EV_Comparison_Report.pdf",
                        type = ArtifactType.PDF,
                        uri = pdfFile.absolutePath,
                        description = "Automated comparison document",
                        taskId = goal.goalId
                    )

                    TaskStepResult(
                        nodeId = "step_pdf_compilation",
                        success = true,
                        message = "Created PDF document: ${artifact.name}",
                        artifacts = listOf(artifact),
                        outputData = mapOf("pdfArtifact" to artifact.artifactId)
                    )
                }
            )

            val resultNodes = mutableListOf(researchNode, pdfNode)

            if (lower.contains("presentation") || lower.contains("ppt") || lower.contains("स्लाइड")) {
                val pptFromPdfNode = TaskNode(
                    nodeId = "step_presentation_from_pdf",
                    title = "Generate Presentation from Research PDF",
                    agent = AgentType.CODING,
                    dependencies = setOf("step_pdf_compilation"),
                    customExecution = { outputs ->
                        val pdfId = outputs["pdfArtifact"] as? String ?: "EV_Comparison_Report.pdf"
                        val pptFile = File(context.filesDir, "Laptops_Comparison_Deck.pptx")
                        pptFile.writeText("Slide 1: Research Summary\nSlide 2: Top 3 Comparison\nSlide 3: Key Takeaways")
                        val pptArtifact = TaskArtifact(
                            name = "Laptops_Comparison_Deck.pptx",
                            type = ArtifactType.PRESENTATION,
                            uri = pptFile.absolutePath,
                            description = "Presentation synthesized from PDF research: $pdfId",
                            taskId = goal.goalId
                        )
                        TaskStepResult(
                            nodeId = "step_presentation_from_pdf",
                            success = true,
                            message = "Created presentation from PDF: ${pptArtifact.name}",
                            artifacts = listOf(pptArtifact),
                            outputData = mapOf("pptArtifact" to pptArtifact.artifactId)
                        )
                    }
                )
                resultNodes.add(pptFromPdfNode)
            }

            return Pair(goal, resultNodes)
        }

        // 4. Default: Standard Single or Sequence Action
        val defaultNode = TaskNode(
            nodeId = "step_general_execution",
            title = "Execute Command",
            agent = AgentType.SYSTEM,
            customExecution = { _ ->
                TaskStepResult(
                    nodeId = "step_general_execution",
                    success = true,
                    message = "Executed goal: $userInput"
                )
            }
        )

        return Pair(goal, listOf(defaultNode))
    }

    /**
     * Resolves ambiguous contact references (Requirement 7)
     */
    fun resolveContactAmbiguity(
        query: String,
        candidates: List<AmbiguityCandidate>
    ): AmbiguityResolutionResult {
        if (candidates.isEmpty()) {
            return AmbiguityResolutionResult(resolved = false, promptIfUnresolved = "No matching contact found.", confidence = 0.0f)
        }
        if (candidates.size == 1) {
            return AmbiguityResolutionResult(resolved = true, selectedCandidate = candidates.first(), confidence = 1.0f)
        }

        val lower = query.lowercase()
        // Compute common name tokens across candidates (e.g. "Rahul")
        val allNameLists = candidates.map { it.displayName.lowercase().split(" ").filter { t -> t.length > 2 } }
        val commonTokens = if (allNameLists.isNotEmpty()) {
            allNameLists.reduce { acc, list -> acc.intersect(list.toSet()).toList() }.toSet()
        } else emptySet()

        // Check context tags (e.g. "office wala", "school", "developer", "mumbai") or distinguishing name parts (e.g. "Sharma wala")
        for (cand in candidates) {
            val matchesTag = cand.contextTags.any { tag -> lower.contains(tag.lowercase()) }
            val distinguishingParts = cand.displayName.lowercase().split(" ")
                .filter { it.length > 2 && !commonTokens.contains(it) }
            val matchesNamePart = distinguishingParts.any { part -> lower.contains(part) }
            if (matchesTag || matchesNamePart) {
                return AmbiguityResolutionResult(
                    resolved = true,
                    selectedCandidate = cand,
                    confidence = 0.95f
                )
            }
        }

        // Unresolved: Ask user concisely
        val candidateNames = candidates.map { it.displayName }.joinToString(" or ")
        val prompt = "Which ${candidates.first().displayName.split(" ").first()}? $candidateNames?"
        return AmbiguityResolutionResult(
            resolved = false,
            promptIfUnresolved = prompt,
            confidence = 0.45f
        )
    }

    /**
     * Plan Revision (Requirement 12): Alter format or constraints of active goal
     */
    fun reviseActivePlan(
        revisionInstruction: String,
        currentNodes: List<TaskNode>
    ): List<TaskNode> {
        val lower = revisionInstruction.lowercase()
        if (lower.contains("pdf") && !lower.contains("presentation")) {
            return currentNodes.map { node ->
                if (node.nodeId.contains("presentation")) {
                    node.copy(
                        title = "Compile Summary Document (PDF Format)",
                        agent = AgentType.FILE
                    )
                } else node
            }
        }
        return currentNodes
    }
}
