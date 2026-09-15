package com.pantham.nexus.prediction

data class UpcomingContextEvent(
    val title: String,
    val startTime: Long,
    val category: String? = null,
    val location: String? = null,
    val relatedEntityIds: List<String> =
        emptyList()
)

class NexusPreparationPredictor(
    private val confidenceEngine:
        NexusPredictionConfidenceEngine
) {

    fun predict(
        event: UpcomingContextEvent,
        now: Long = System.currentTimeMillis()
    ): List<NexusPrediction> {

        if (event.startTime <= now) {
            return emptyList()
        }

        val until =
            event.startTime - now

        val hours =
            until / (60L * 60L * 1000L)

        if (hours > 48) {
            return emptyList()
        }

        val evidence =
            listOf(
                PredictionEvidence(
                    source =
                        PredictionSource.CALENDAR,
                    description =
                        "Upcoming event '${event.title}' is within 48 hours.",
                    weight = 0.95f
                )
            )

        val proximity =
            when {
                hours <= 2 -> 1f
                hours <= 8 -> 0.9f
                hours <= 24 -> 0.75f
                else -> 0.55f
            }

        val score =
            confidenceEngine.calculate(
                evidence = evidence,
                contextMatch = proximity
            )

        val suggestion =
            NexusPrediction(
                type =
                    PredictionType.PREPARATION,
                title =
                    "Prepare for ${event.title}",
                description =
                    "An upcoming event may require preparation.",
                predictedAction =
                    "PREPARE_FOR_EVENT",
                targetEntityId =
                    event.relatedEntityIds
                        .firstOrNull(),
                confidence =
                    confidenceEngine
                        .toConfidence(score),
                confidenceScore =
                    score,
                disposition =
                    PredictionDisposition.SUGGEST,
                evidence =
                    evidence,
                expiresAt =
                    event.startTime,
                metadata =
                    mapOf(
                        "eventTitle" to event.title,
                        "eventStartTime" to
                            event.startTime.toString(),
                        "category" to
                            (event.category ?: ""),
                        "location" to
                            (event.location ?: "")
                    )
            )

        return listOf(suggestion)
    }
}
