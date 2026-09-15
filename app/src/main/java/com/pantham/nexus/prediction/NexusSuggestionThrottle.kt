package com.pantham.nexus.prediction

class NexusSuggestionThrottle(
    private val repository:
        NexusPredictionRepository
) {

    companion object {
        private const val DEFAULT_COOLDOWN =
            30L * 60L * 1000L
    }

    suspend fun shouldShow(
        prediction: NexusPrediction,
        cooldownMillis: Long =
            DEFAULT_COOLDOWN
    ): Boolean {

        val action =
            prediction.predictedAction
                ?: prediction.title

        val similar =
            repository.findSimilarRecent(
                type = prediction.type,
                predictedAction = action,
                since =
                    System.currentTimeMillis() -
                        cooldownMillis
            )

        if (similar.isNotEmpty()) {
            return false
        }

        val feedback =
            repository
                .getRecentFeedback(50)

        val recentRejections =
            feedback.count {
                it.feedback ==
                    PredictionFeedback.REJECTED ||
                    it.feedback ==
                    PredictionFeedback.DISMISSED
            }

        if (recentRejections >= 5) {
            return false
        }

        return true
    }
}
