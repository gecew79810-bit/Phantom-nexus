package com.pantham.nexus.prediction

class NexusPredictionFailureGuard(
    private val controller:
        NexusPredictionController
) {

    suspend fun safeProcess(
        request: PredictionRequest
    ): PredictiveContext {

        return try {

            controller.process(
                request
            )

        } catch (
            throwable: Throwable
        ) {

            PredictiveContext(
                predictions =
                    emptyList(),
                internalHints =
                    emptyList(),
                proactiveCandidates =
                    emptyList()
            )
        }
    }
}
