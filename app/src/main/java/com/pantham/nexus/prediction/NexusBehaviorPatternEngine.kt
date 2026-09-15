package com.pantham.nexus.prediction

data class BehaviorEvent(
    val action: String,
    val timestamp: Long,
    val contextKey: String? = null,
    val success: Boolean = true
)

data class BehaviorPattern(
    val action: String,
    val occurrences: Int,
    val successRate: Float,
    val strength: Float,
    val averageIntervalMillis: Long? = null
)

class NexusBehaviorPatternEngine {

    fun detect(
        events: List<BehaviorEvent>
    ): List<BehaviorPattern> {

        if (events.isEmpty()) {
            return emptyList()
        }

        return events
            .groupBy {
                normalize(it.action)
            }
            .map { (_, group) ->

                val sorted =
                    group.sortedBy {
                        it.timestamp
                    }

                val successRate =
                    group.count {
                        it.success
                    }.toFloat() /
                        group.size.toFloat()

                val intervals =
                    sorted.zipWithNext {
                            first,
                            second ->

                        second.timestamp -
                            first.timestamp
                    }

                val averageInterval =
                    if (intervals.isEmpty()) {
                        null
                    } else {
                        intervals.average().toLong()
                    }

                val occurrenceScore =
                    (
                        group.size.toFloat() /
                            8f
                        ).coerceIn(0f, 1f)

                val strength =
                    (
                        occurrenceScore *
                            0.7f +
                            successRate *
                            0.3f
                        ).coerceIn(0f, 1f)

                BehaviorPattern(
                    action =
                        group.first().action,
                    occurrences =
                        group.size,
                    successRate =
                        successRate,
                    strength =
                        strength,
                    averageIntervalMillis =
                        averageInterval
                )
            }
            .sortedByDescending {
                it.strength
            }
    }

    private fun normalize(
        text: String
    ): String =
        text.trim()
            .lowercase()
            .replace(
                Regex("\\s+"),
                " "
            )
}
