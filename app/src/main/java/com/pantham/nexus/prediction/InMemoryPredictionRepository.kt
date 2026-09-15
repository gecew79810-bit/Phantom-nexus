package com.pantham.nexus.prediction

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryPredictionRepository :
    NexusPredictionRepository {

    private val mutex = Mutex()

    private val predictions =
        LinkedHashMap<String, NexusPrediction>()

    private val feedback =
        mutableListOf<PredictionFeedbackRecord>()

    override suspend fun savePrediction(
        prediction: NexusPrediction
    ) {
        mutex.withLock {
            predictions[prediction.id] =
                prediction
        }
    }

    override suspend fun getPrediction(
        id: String
    ): NexusPrediction? =
        mutex.withLock {
            predictions[id]
        }

    override suspend fun getRecentPredictions(
        limit: Int
    ): List<NexusPrediction> =
        mutex.withLock {
            predictions.values
                .sortedByDescending {
                    it.createdAt
                }
                .take(limit)
        }

    override suspend fun findSimilarRecent(
        type: PredictionType,
        predictedAction: String?,
        since: Long
    ): List<NexusPrediction> =
        mutex.withLock {

            predictions.values.filter {

                it.type == type &&
                    it.createdAt >= since &&
                    (
                        predictedAction == null ||
                            it.predictedAction
                                ?.equals(
                                    predictedAction,
                                    ignoreCase = true
                                ) == true
                        )
            }
        }

    override suspend fun saveFeedback(
        feedback: PredictionFeedbackRecord
    ) {
        mutex.withLock {
            this.feedback += feedback
        }
    }

    override suspend fun getFeedback(
        predictionId: String
    ): List<PredictionFeedbackRecord> =
        mutex.withLock {
            feedback.filter {
                it.predictionId ==
                    predictionId
            }
        }

    override suspend fun getRecentFeedback(
        limit: Int
    ): List<PredictionFeedbackRecord> =
        mutex.withLock {
            feedback
                .sortedByDescending {
                    it.timestamp
                }
                .take(limit)
        }

    override suspend fun removeExpired(
        now: Long
    ) {
        mutex.withLock {

            val expired =
                predictions.values
                    .filter {
                        it.expiresAt != null &&
                            it.expiresAt <= now
                    }
                    .map {
                        it.id
                    }

            expired.forEach {
                predictions.remove(it)
            }
        }
    }
}
