package com.pantham.nexus.learning.context

import com.pantham.nexus.learning.model.*
import com.pantham.nexus.learning.runtime.NexusAdaptiveLearningRuntime

class NexusLearningContextAdapter(
    private val runtime: NexusAdaptiveLearningRuntime = NexusAdaptiveLearningRuntime.getInstance()
) {

    suspend fun buildContext(): AdaptiveLearningContext {
        val context = runtime.getContext()

        return context.copy(
            preferences =
                context.preferences
                    .sortedByDescending { it.strength }
                    .take(20),
            adjustments =
                context.adjustments
                    .sortedByDescending { it.updatedAt }
                    .take(20),
            recentFeedback =
                context.recentFeedback
                    .sortedByDescending { it.recordedAt }
                    .take(20)
        )
    }
}
