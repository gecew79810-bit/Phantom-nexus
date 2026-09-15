package com.pantham.nexus.decision.engine

import com.pantham.nexus.decision.model.*

class NexusDecisionEngine {

    private val criteriaEngine =
        NexusDecisionCriteriaEngine()

    private val scoringEngine =
        NexusDecisionScoringEngine()

    private val riskEngine =
        NexusDecisionRiskEngine()

    private val tradeoffEngine =
        NexusDecisionTradeoffEngine()

    private val missingEngine =
        NexusDecisionMissingInfoEngine()

    private val recommendationEngine =
        NexusDecisionRecommendationEngine()

    private val confidenceEngine =
        NexusDecisionConfidenceEngine()


    fun analyze(
        request: DecisionRequest
    ): DecisionAnalysis {

        val criteria =
            criteriaEngine.buildCriteria(
                request
            )

        val scores =
            scoringEngine.score(
                request =
                    request,
                options =
                    request.options,
                criteria =
                    criteria
            )

        val risks =
            request.options.flatMap {
                riskEngine.assess(
                    request,
                    it
                )
            }

        val tradeoffs =
            tradeoffEngine.detect(
                scores =
                    scores,
                criteria =
                    criteria,
                options =
                    request.options
            )

        val missing =
            missingEngine.detect(
                request =
                    request,
                options =
                    request.options,
                criteria =
                    criteria
            )

        val recommendation =
            recommendationEngine.recommend(
                options =
                    request.options,
                scores =
                    scores,
                risks =
                    risks,
                tradeoffs =
                    tradeoffs,
                missing =
                    missing,
                maxAlternatives =
                    request.maxAlternatives
            )

        var analysis =
            DecisionAnalysis(
                request =
                    request,
                scores =
                    scores,
                risks =
                    risks,
                tradeoffs =
                    tradeoffs,
                missingInformation =
                    missing,
                recommendation =
                    recommendation,
                confidence =
                    DecisionConfidence.VERY_LOW
            )

        analysis =
            analysis.copy(
                confidence =
                    confidenceEngine.calculate(
                        analysis
                    )
            )

        return analysis
    }
}
