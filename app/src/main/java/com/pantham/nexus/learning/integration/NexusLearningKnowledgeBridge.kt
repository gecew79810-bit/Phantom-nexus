package com.pantham.nexus.learning.integration

import com.pantham.nexus.knowledge.KnowledgeConfidence
import com.pantham.nexus.knowledge.KnowledgeObservation
import com.pantham.nexus.knowledge.KnowledgeSource
import com.pantham.nexus.knowledge.NexusKnowledgeRepository
import com.pantham.nexus.learning.model.LearnedPreference
import com.pantham.nexus.learning.model.LearningConfidence
import com.pantham.nexus.learning.model.PreferencePolarity
import com.pantham.nexus.learning.security.NexusLearningPrivacyGate
import java.util.UUID

interface NexusLearningKnowledgeBridge {

    suspend fun publishPreference(
        preference: LearnedPreference
    )
}

/**
 * Concrete bridge that publishes high-reliability learned preferences
 * to the canonical Personal Knowledge Core without creating duplicate knowledge stores.
 */
class NexusLearningKnowledgeBridgeImpl(
    private val knowledgeRepository: NexusKnowledgeRepository,
    private val privacyGate: NexusLearningPrivacyGate = NexusLearningPrivacyGate()
) : NexusLearningKnowledgeBridge {

    override suspend fun publishPreference(preference: LearnedPreference) {
        // Privacy Guardrail: Never publish sensitive data
        val dummySignal = com.pantham.nexus.learning.model.LearningSignal(
            id = UUID.randomUUID().toString(),
            type = com.pantham.nexus.learning.model.LearningSignalType.EXPLICIT_PREFERENCE,
            key = preference.key,
            value = preference.value,
            confidence = preference.strength,
            scope = preference.scope,
            source = "knowledge_publish"
        )
        if (!privacyGate.shouldLearn(dummySignal)) {
            return
        }

        // Reliability Threshold: Only publish sufficiently reliable preferences
        if (preference.confidence < LearningConfidence.HIGH || preference.strength < 0.70f) {
            return
        }

        val kConfidence = when (preference.confidence) {
            LearningConfidence.VERY_HIGH -> KnowledgeConfidence.VERY_HIGH
            LearningConfidence.HIGH -> KnowledgeConfidence.HIGH
            LearningConfidence.MEDIUM -> KnowledgeConfidence.MEDIUM
            LearningConfidence.LOW -> KnowledgeConfidence.LOW
            LearningConfidence.VERY_LOW -> KnowledgeConfidence.VERY_LOW
        }

        val predicate = if (preference.polarity == PreferencePolarity.DISLIKE) {
            "DISLIKES"
        } else {
            "PREFERS"
        }

        val observation = KnowledgeObservation(
            id = "pref_${UUID.randomUUID()}",
            subjectEntityId = "user_preference",
            predicate = predicate,
            value = "${preference.key}:${preference.value}",
            source = if (preference.strength >= 0.95f) KnowledgeSource.USER_EXPLICIT else KnowledgeSource.INFERRED,
            confidence = kConfidence,
            evidence = "Learned preference strength: ${preference.strength} (evidence count: ${preference.evidenceCount})"
        )

        knowledgeRepository.addObservation(observation)
    }
}
