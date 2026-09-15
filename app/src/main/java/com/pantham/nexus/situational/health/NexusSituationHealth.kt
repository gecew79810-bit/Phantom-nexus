package com.pantham.nexus.situational.health

data class SituationHealth(
    val healthy: Boolean,
    val providerFailures: Int,
    val lastSuccessfulBuildAt: Long,
    val message: String
)

class NexusSituationHealthManager {

    private var failureCount: Int = 0
    private var lastSuccessAt: Long = 0L

    fun recordSuccess() {
        failureCount = 0
        lastSuccessAt = System.currentTimeMillis()
    }

    fun recordFailure() {
        failureCount++
    }

    fun snapshot(): SituationHealth {
        return SituationHealth(
            healthy = failureCount < 5,
            providerFailures = failureCount,
            lastSuccessfulBuildAt = lastSuccessAt,
            message =
                if (failureCount == 0)
                    "Situational awareness healthy."
                else
                    "Situational awareness operating with degraded providers."
        )
    }
}
