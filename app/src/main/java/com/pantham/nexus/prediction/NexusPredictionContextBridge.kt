package com.pantham.nexus.prediction

import com.pantham.nexus.intelligence.model.NexusContext

interface NexusPredictionContextBridge {
    suspend fun buildPredictionContext(
        nexusContext: NexusContext,
        actionRequirement: ActionRequirement? = null,
        upcomingEvents: List<UpcomingContextEvent> = emptyList(),
        behaviorEvents: List<BehaviorEvent> = emptyList(),
        plannedRiskInput: PlannedActionRiskInput? = null
    ): PredictionRequest

    suspend fun predictForContext(
        nexusContext: NexusContext
    ): PredictiveContext
}

class StandardPredictionContextBridge(
    private val controller: NexusPredictionController,
    private val failureGuard: NexusPredictionFailureGuard = NexusPredictionFailureGuard(controller)
) : NexusPredictionContextBridge {

    override suspend fun buildPredictionContext(
        nexusContext: NexusContext,
        actionRequirement: ActionRequirement?,
        upcomingEvents: List<UpcomingContextEvent>,
        behaviorEvents: List<BehaviorEvent>,
        plannedRiskInput: PlannedActionRiskInput?
    ): PredictionRequest {
        val memorySignals: List<String> = nexusContext.relevantMemories.map { it.content }
        val knowledgeSignals: List<String> = nexusContext.knowledgeContext?.relevantEntities?.map { it.canonicalName } ?: emptyList()
        val visionSignals: List<String> = listOfNotNull(nexusContext.visualContext?.description, nexusContext.visualContext?.visibleText?.takeIf { it.isNotBlank() })
        val entityIds: List<String> = nexusContext.recentEntities.map { it.id }

        val predContext = PredictionContext(
            userText = nexusContext.userInput,
            activeGoalId = nexusContext.taskId,
            resolvedEntityIds = entityIds,
            currentTimeMillis = nexusContext.currentTimeMillis,
            foregroundPackage = nexusContext.currentPackage,
            deviceState = mapOf(
                "networkAvailable" to nexusContext.networkAvailable.toString(),
                "batteryPercent" to (nexusContext.batteryPercent?.toString() ?: "")
            ),
            memorySignals = memorySignals,
            knowledgeSignals = knowledgeSignals,
            visionSignals = visionSignals
        )

        return PredictionRequest(
            context = predContext,
            actionRequirement = actionRequirement,
            upcomingEvents = upcomingEvents,
            behaviorEvents = behaviorEvents,
            plannedRiskInput = plannedRiskInput
        )
    }

    override suspend fun predictForContext(nexusContext: NexusContext): PredictiveContext {
        val req = buildPredictionContext(nexusContext)
        return failureGuard.safeProcess(req)
    }
}
