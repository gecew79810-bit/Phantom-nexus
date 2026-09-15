package com.pantham.nexus.prediction

class NexusPredictionFeedbackEngine(
    private val repository:
        NexusPredictionRepository
) {

    suspend fun record(
        predictionId: String,
        feedback: PredictionFeedback,
        correction: String? = null
    ) {

        repository.saveFeedback(
            PredictionFeedbackRecord(
                predictionId =
                    predictionId,
                feedback =
                    feedback,
                correction =
                    correction
            )
        )
    }

    suspend fun acceptanceRate(
        limit: Int = 100
    ): Float {

        val history =
            repository
                .getRecentFeedback(limit)
                .filter {
                    it.feedback ==
                        PredictionFeedback.ACCEPTED ||
                        it.feedback ==
                        PredictionFeedback.REJECTED ||
                        it.feedback ==
                        PredictionFeedback.DISMISSED
                }

        if (history.isEmpty()) {
            return 0.5f
        }

        val accepted =
            history.count {
                it.feedback ==
                    PredictionFeedback.ACCEPTED
            }

        return accepted.toFloat() /
            history.size.toFloat()
    }
}
