package com.pantham.nexus.learning.integration

import com.pantham.nexus.learning.model.LearnedPreference
import com.pantham.nexus.learning.model.PreferencePolarity

interface NexusLearningPredictionConsumer {

    suspend fun applyPreference(
        preference: LearnedPreference
    )
}

/**
 * Concrete bridge that provides learned preferences to Prediction Core
 * as supporting signals without treating them as absolute truth.
 */
class NexusLearningPredictionBridgeImpl : NexusLearningPredictionConsumer {

    private val _supportingHints = mutableListOf<String>()
    val supportingHints: List<String> get() = _supportingHints.toList()

    override suspend fun applyPreference(preference: LearnedPreference) {
        val polarityText = if (preference.polarity == PreferencePolarity.PREFER) "prefers" else "dislikes"
        val hint = "User historically $polarityText ${preference.value} for ${preference.key} (supporting weight: ${preference.strength})"
        if (!_supportingHints.contains(hint)) {
            if (_supportingHints.size >= 20) {
                _supportingHints.removeAt(0)
            }
            _supportingHints.add(hint)
        }
    }
}
