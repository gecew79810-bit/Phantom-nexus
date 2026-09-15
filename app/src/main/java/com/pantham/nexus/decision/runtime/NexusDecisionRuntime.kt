package com.pantham.nexus.decision.runtime

import com.pantham.nexus.decision.engine.NexusDecisionEngine
import com.pantham.nexus.decision.model.*
import com.pantham.nexus.decision.security.NexusDecisionSafetyGate

class NexusDecisionRuntime {

    private val engine =
        NexusDecisionEngine()

    private val safetyGate =
        NexusDecisionSafetyGate()

    private val history =
        ArrayDeque<DecisionAnalysis>()

    suspend fun analyze(
        request: DecisionRequest
    ): DecisionAnalysis {

        val analysis =
            engine.analyze(
                request
            )

        val safe =
            safetyGate.evaluate(
                analysis
            )

        val finalAnalysis =
            if (safe) {
                analysis
            } else {
                analysis.copy(
                    recommendation =
                        analysis.recommendation
                            ?.copy(
                                confidence =
                                    DecisionConfidence.LOW,
                                reason =
                                    analysis.recommendation
                                        .reason +
                                        " Recommendation confidence has been reduced because important uncertainty or risk remains."
                            )
                )
            }

        history.addLast(
            finalAnalysis
        )

        while (
            history.size > 20
        ) {
            history.removeFirst()
        }

        return finalAnalysis
    }


    fun recentDecisions():
        List<DecisionAnalysis> =
        history.toList()


    fun current():
        DecisionAnalysis? =
        history.lastOrNull()
}
