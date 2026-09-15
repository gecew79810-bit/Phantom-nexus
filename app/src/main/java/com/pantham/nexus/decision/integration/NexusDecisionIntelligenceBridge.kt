package com.pantham.nexus.decision.integration

import com.pantham.nexus.decision.model.*

class NexusDecisionIntelligenceBridge {

    fun enrichRequest(
        request: DecisionRequest,
        external:
            DecisionExternalContext
    ): DecisionRequest {

        val bridge =
            NexusDecisionContextBridge()

        val additional =
            bridge.toEvidence(
                external
            )

        return request.copy(
            evidence =
                request.evidence +
                    additional
        )
    }


    fun explain(
        analysis: DecisionAnalysis
    ): String {

        val recommendation =
            analysis.recommendation

        if (recommendation == null) {

            val missing =
                analysis.missingInformation
                    .filter {
                        it.blocking
                    }

            return if (missing.isNotEmpty()) {

                "I need more information before making a reliable recommendation: " +
                    missing.joinToString("; ") {
                        it.reason
                    }

            } else {
                "I could not produce a sufficiently supported recommendation."
            }
        }

        val result =
            StringBuilder()

        result.append(
            "Recommendation: ${recommendation.optionName}. "
        )

        result.append(
            recommendation.reason
        )

        result.append(
            " Confidence: ${recommendation.confidence.name.lowercase()}."
        )

        if (
            recommendation.risks.isNotEmpty()
        ) {

            result.append(
                " Main risk: "
            )

            result.append(
                recommendation.risks
                    .maxBy {
                        it.severity
                    }
                    .description
            )
        }

        if (
            recommendation.alternatives.isNotEmpty()
        ) {

            result.append(
                " Alternative: "
            )

            result.append(
                recommendation.alternatives
                    .first()
                    .reason
            )
        }

        return result.toString()
    }
}
