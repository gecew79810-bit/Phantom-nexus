package com.pantham.nexus.vision.bridge

import com.pantham.nexus.intelligence.context.NexusContextAssembler
import com.pantham.nexus.intelligence.model.InputChannel
import com.pantham.nexus.vision.context.NexusVisualContextBuilder
import com.pantham.nexus.vision.model.VisionAnalysisResult

class VisionIntelligenceBridge(
    private val visualContextBuilder:
        NexusVisualContextBuilder,
    private val intelligenceContext:
        NexusContextAssembler
) {

    suspend fun createContext(
        conversationId: String,
        taskId: String?,
        userInstruction: String,
        visionResult: VisionAnalysisResult
    ): VisionEnhancedContext {

        val visualContext =
            visualContextBuilder.build(
                visionResult
            )

        val context =
            intelligenceContext.build(
                conversationId =
                    conversationId,
                taskId =
                    taskId,
                input =
                    userInstruction,
                channel =
                    InputChannel.VISION
            )

        val enrichedContext =
            context.copy(
                visualContext = visualContext
            )

        return VisionEnhancedContext(
            baseContext =
                enrichedContext,
            visualContext =
                visualContext
        )
    }
}

data class VisionEnhancedContext(
    val baseContext:
        com.pantham.nexus.intelligence.model.NexusContext,

    val visualContext:
        com.pantham.nexus.vision.model.VisualContext
)
