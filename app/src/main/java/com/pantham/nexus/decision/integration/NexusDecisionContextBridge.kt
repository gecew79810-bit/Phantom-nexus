package com.pantham.nexus.decision.integration

import com.pantham.nexus.decision.model.DecisionEvidence
import com.pantham.nexus.decision.model.EvidenceType

data class DecisionExternalContext(
    val fileEvidence: List<String> = emptyList(),
    val knowledgeEvidence: List<String> = emptyList(),
    val visionEvidence: List<String> = emptyList(),
    val predictionEvidence: List<String> = emptyList(),
    val situationEvidence: List<String> = emptyList(),
    val learningEvidence: List<String> = emptyList()
)

class NexusDecisionContextBridge {

    fun toEvidence(
        context: DecisionExternalContext
    ): List<DecisionEvidence> {

        val result =
            mutableListOf<DecisionEvidence>()

        context.fileEvidence.forEach {
            result += DecisionEvidence(
                type =
                    EvidenceType.FILE,
                statement =
                    it,
                reliability =
                    0.75f,
                relevance =
                    0.75f
            )
        }

        context.knowledgeEvidence.forEach {
            result += DecisionEvidence(
                type =
                    EvidenceType.KNOWLEDGE,
                statement =
                    it,
                reliability =
                    0.70f,
                relevance =
                    0.75f
            )
        }

        context.visionEvidence.forEach {
            result += DecisionEvidence(
                type =
                    EvidenceType.VISION,
                statement =
                    it,
                reliability =
                    0.55f,
                relevance =
                    0.65f
            )
        }

        context.predictionEvidence.forEach {
            result += DecisionEvidence(
                type =
                    EvidenceType.PREDICTION,
                statement =
                    it,
                reliability =
                    0.45f,
                relevance =
                    0.55f
            )
        }

        context.situationEvidence.forEach {
            result += DecisionEvidence(
                type =
                    EvidenceType.SITUATION,
                statement =
                    it,
                reliability =
                    0.65f,
                relevance =
                    0.75f
            )
        }

        context.learningEvidence.forEach {
            result += DecisionEvidence(
                type =
                    EvidenceType.LEARNED_PREFERENCE,
                statement =
                    it,
                reliability =
                    0.70f,
                relevance =
                    0.75f
            )
        }

        return result
    }
}
