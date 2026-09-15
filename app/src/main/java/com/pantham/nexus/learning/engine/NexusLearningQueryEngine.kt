package com.pantham.nexus.learning.engine

import com.pantham.nexus.learning.model.*
import com.pantham.nexus.learning.repository.NexusLearningRepository

class NexusLearningQueryEngine(
    private val repository: NexusLearningRepository
) {

    suspend fun getStrongPreferences(): List<LearnedPreference> {
        return repository
            .getPreferences()
            .filter {
                it.confidence >= LearningConfidence.MEDIUM
            }
            .sortedWith(
                compareByDescending<LearnedPreference> {
                    it.strength
                }.thenByDescending {
                    it.evidenceCount
                }
            )
    }

    suspend fun getPreference(
        key: String
    ): LearnedPreference? {
        return repository
            .getPreferences(
                LearningQuery(
                    key = key
                )
            )
            .maxByOrNull {
                it.strength
            }
    }

    suspend fun shouldPrefer(
        key: String,
        value: String
    ): Boolean {
        val preference =
            repository
                .getPreferences(
                    LearningQuery(
                        key = key
                    )
                )
                .firstOrNull {
                    it.value.equals(
                        value,
                        ignoreCase = true
                    )
                }

        return preference != null &&
            preference.polarity == PreferencePolarity.PREFER &&
            preference.confidence >= LearningConfidence.MEDIUM
    }
}
