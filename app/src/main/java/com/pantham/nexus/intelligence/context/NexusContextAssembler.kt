package com.pantham.nexus.intelligence.context

import com.pantham.nexus.intelligence.model.InputChannel
import com.pantham.nexus.intelligence.model.MemoryCandidate
import com.pantham.nexus.intelligence.model.NexusContext
import com.pantham.nexus.intelligence.model.ResolvedEntity
import com.pantham.nexus.knowledge.NexusKnowledgeContextAdapter

interface NexusRuntimeContextProvider {
    suspend fun currentPackage(): String?
    suspend fun currentScreenSummary(): String?
    suspend fun currentLocation(): String?
    suspend fun batteryPercent(): Int?
    suspend fun networkAvailable(): Boolean
    suspend fun currentMedia(): String?
    suspend fun recentEntities(): List<ResolvedEntity>
}

interface RelevantMemoryResolver {
    suspend fun findRelevantMemories(
        input: String,
        limit: Int = 5
    ): List<MemoryCandidate>
}

class NexusContextAssembler(
    private val runtimeProvider: NexusRuntimeContextProvider,
    private val memoryResolver: RelevantMemoryResolver,
    private val knowledgeAdapter: NexusKnowledgeContextAdapter? = null,
    private val predictionBridge: com.pantham.nexus.prediction.NexusPredictionContextBridge? = null,
    private val situationAdapter: com.pantham.nexus.situational.integration.NexusSituationContextAdapter? = null,
    private val fileAdapter: com.pantham.nexus.files.adapter.NexusFileContextAdapter? = null,
    private val decisionAdapter: com.pantham.nexus.decision.adapter.NexusDecisionContextAdapter? = null,
    private val learningAdapter: com.pantham.nexus.learning.context.NexusLearningContextAdapter? = null
) {

    suspend fun build(
        conversationId: String,
        taskId: String?,
        input: String,
        channel: InputChannel
    ): NexusContext {
        val memories = memoryResolver.findRelevantMemories(input = input, limit = 5)
        val currentPackage = runtimeProvider.currentPackage()
        val currentScreen = runtimeProvider.currentScreenSummary()
        val location = runtimeProvider.currentLocation()
        val battery = runtimeProvider.batteryPercent()
        val network = runtimeProvider.networkAvailable()
        val media = runtimeProvider.currentMedia()
        val entities = runtimeProvider.recentEntities()
        val knowledgeContext = knowledgeAdapter?.buildRelevantContext(input)

        val preliminaryContext = NexusContext(
            conversationId = conversationId,
            taskId = taskId,
            inputChannel = channel,
            userInput = input,
            currentTimeMillis = System.currentTimeMillis(),
            currentPackage = currentPackage,
            currentScreenSummary = currentScreen,
            currentLocation = location,
            batteryPercent = battery,
            networkAvailable = network,
            currentMedia = media,
            relevantMemories = memories,
            recentEntities = entities,
            knowledgeContext = knowledgeContext
        )

        val predictiveContext = predictionBridge?.predictForContext(preliminaryContext)
        val situationalContext = try {
            situationAdapter?.getSituationalContext()
        } catch (_: Throwable) {
            null
        }

        val fileIntelligenceContext = try {
            fileAdapter?.getFileIntelligenceContext(activeQuery = input)
        } catch (_: Throwable) {
            null
        }

        val decisionContext = try {
            decisionAdapter?.getDecisionContext()
        } catch (_: Throwable) {
            null
        }

        val adaptiveLearningContext = try {
            learningAdapter?.buildContext()
        } catch (_: Throwable) {
            null
        }

        return preliminaryContext.copy(
            predictiveContext = predictiveContext,
            situationalContext = situationalContext,
            fileIntelligenceContext = fileIntelligenceContext,
            decisionContext = decisionContext,
            adaptiveLearningContext = adaptiveLearningContext
        )
    }
}
