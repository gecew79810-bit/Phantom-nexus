package com.pantham.nexus.decision.engine

import com.pantham.nexus.decision.model.*

class NexusDecisionRecommendationEngine {

    fun recommend(
        options: List<DecisionOption>,
        scores: List<DecisionScore>,
        risks: List<DecisionRiskItem>,
        tradeoffs: List<DecisionTradeoff>,
        missing: List<MissingDecisionInformation>,
        maxAlternatives: Int
    ): DecisionRecommendation? {

        if (options.isEmpty()) {
            return null
        }

        val blocked =
            missing.any {
                it.blocking
            }

        if (blocked) {
            return null
        }

        val optionTotals =
            options.associate { option ->

                option.id to
                    scores
                        .filter {
                            it.optionId ==
                                option.id
                        }
                        .sumOf {
                            it.weightedScore
                                .toDouble()
                        }
                        .toFloat()
            }

        val ranked =
            optionTotals
                .entries
                .sortedByDescending {
                    it.value
                }

        val winner =
            ranked.firstOrNull()
                ?: return null

        val winnerOption =
            options.firstOrNull {
                it.id == winner.key
            } ?: return null

        val winnerRisks =
            risks.filter {
                it.optionId ==
                    winner.key
            }

        val alternatives =
            ranked
                .drop(1)
                .take(
                    maxAlternatives
                        .coerceIn(0, 5)
                )
                .map { entry ->

                    val option =
                        options.firstOrNull {
                            it.id == entry.key
                        }

                    DecisionAlternative(
                        optionId =
                            entry.key,
                        reason =
                            option?.name
                                ?.let {
                                    "$it remains a viable alternative."
                                }
                                ?: "Alternative option."
                    )
                }

        val score =
            winner.value
                .coerceIn(0f, 1f)

        val confidence =
            calculateConfidence(
                score,
                winnerRisks,
                missing
            )

        val reason =
            buildReason(
                winnerOption,
                score,
                winnerRisks,
                tradeoffs
            )

        return DecisionRecommendation(
            optionId =
                winnerOption.id,
            optionName =
                winnerOption.name,
            confidence =
                confidence,
            score =
                score,
            reason =
                reason,
            risks =
                winnerRisks,
            tradeoffs =
                tradeoffs,
            alternatives =
                alternatives
        )
    }


    private fun calculateConfidence(
        score: Float,
        risks: List<DecisionRiskItem>,
        missing: List<MissingDecisionInformation>
    ): DecisionConfidence {

        val riskPenalty =
            risks
                .maxOfOrNull {
                    it.severity
                } ?: 0f

        val missingPenalty =
            missing
                .sumOf {
                    (it.importance * 0.20f)
                        .toDouble()
                }
                .toFloat()
                .coerceAtMost(0.45f)

        val confidence =
            (
                score -
                riskPenalty * 0.25f -
                missingPenalty
            ).coerceIn(0f, 1f)

        return when {
            confidence >= 0.85f ->
                DecisionConfidence.VERY_HIGH

            confidence >= 0.70f ->
                DecisionConfidence.HIGH

            confidence >= 0.50f ->
                DecisionConfidence.MEDIUM

            confidence >= 0.30f ->
                DecisionConfidence.LOW

            else ->
                DecisionConfidence.VERY_LOW
        }
    }


    private fun buildReason(
        option: DecisionOption,
        score: Float,
        risks: List<DecisionRiskItem>,
        tradeoffs: List<DecisionTradeoff>
    ): String {

        val reason =
            StringBuilder()

        reason.append(
            "${option.name} ranks highest based on the available evidence and criteria"
        )

        reason.append(
            " (score ${"%.2f".format(score)})."
        )

        if (risks.isNotEmpty()) {
            reason.append(
                " ${risks.size} risk consideration(s) remain."
            )
        }

        if (tradeoffs.isNotEmpty()) {
            reason.append(
                " The recommendation involves trade-offs."
            )
        }

        return reason.toString()
    }
}
