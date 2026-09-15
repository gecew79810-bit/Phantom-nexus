package com.pantham.nexus.prediction

interface NexusPredictionRepository {

    suspend fun savePrediction(
        prediction: NexusPrediction
    )

    suspend fun savePredictions(
        predictions: List<NexusPrediction>
    ) {
        predictions.forEach {
            savePrediction(it)
        }
    }

    suspend fun getPrediction(
        id: String
    ): NexusPrediction?

    suspend fun getRecentPredictions(
        limit: Int = 50
    ): List<NexusPrediction>

    suspend fun findSimilarRecent(
        type: PredictionType,
        predictedAction: String?,
        since: Long
    ): List<NexusPrediction>

    suspend fun saveFeedback(
        feedback: PredictionFeedbackRecord
    )

    suspend fun getFeedback(
        predictionId: String
    ): List<PredictionFeedbackRecord>

    suspend fun getRecentFeedback(
        limit: Int = 100
    ): List<PredictionFeedbackRecord>

    suspend fun removeExpired(
        now: Long = System.currentTimeMillis()
    )
}
