package com.pantham.nexus.decision.adapter

import com.pantham.nexus.decision.model.DecisionAnalysis
import com.pantham.nexus.decision.model.DecisionContext
import com.pantham.nexus.decision.runtime.NexusDecisionRuntime

class NexusDecisionContextAdapter(
    private val runtime: NexusDecisionRuntime = NexusDecisionRuntime()
) {

    private var activeAnalysis: DecisionAnalysis? = null
    private val relevantFiles = mutableListOf<String>()
    private val relevantKnowledge = mutableListOf<String>()

    fun updateActiveDecision(
        analysis: DecisionAnalysis?,
        fileIds: List<String> = emptyList(),
        knowledgeIds: List<String> = emptyList()
    ) {
        activeAnalysis = analysis
        if (fileIds.isNotEmpty()) {
            relevantFiles.clear()
            relevantFiles.addAll(fileIds.take(10))
        }
        if (knowledgeIds.isNotEmpty()) {
            relevantKnowledge.clear()
            relevantKnowledge.addAll(knowledgeIds.take(10))
        }
    }

    fun getDecisionContext(): DecisionContext {
        val recents = runtime.recentDecisions().takeLast(5)
        return DecisionContext(
            recentDecisions = recents,
            activeDecision = activeAnalysis ?: runtime.current(),
            relevantFiles = relevantFiles.toList(),
            relevantKnowledge = relevantKnowledge.toList()
        )
    }

    fun getRuntime(): NexusDecisionRuntime = runtime
}
