package com.pantham.nexus.prediction

class NexusPredictionConfidenceEngine {

    fun calculate(
        evidence: List<PredictionEvidence>,
        patternStrength: Float = 0f,
        contextMatch: Float = 0f,
        historicalAcceptance: Float = 0.5f
    ): Float {

        if (
            evidence.isEmpty() &&
            patternStrength <= 0f &&
            contextMatch <= 0f
        ) {
            return 0f
        }

        val evidenceScore =
            if (evidence.isEmpty()) {
                0f
            } else {
                evidence
                    .map {
                        sourceReliability(it.source) *
                            it.weight.coerceIn(0f, 1f)
                    }
                    .average()
                    .toFloat()
            }

        return (
            evidenceScore * 0.40f +
                patternStrength.coerceIn(0f, 1f) *
                0.25f +
                contextMatch.coerceIn(0f, 1f) *
                0.25f +
                historicalAcceptance
                    .coerceIn(0f, 1f) *
                0.10f
            ).coerceIn(0f, 1f)
    }

    fun toConfidence(
        score: Float
    ): PredictionConfidence =
        when {
            score >= 0.88f ->
                PredictionConfidence.VERY_HIGH

            score >= 0.70f ->
                PredictionConfidence.HIGH

            score >= 0.50f ->
                PredictionConfidence.MEDIUM

            score >= 0.30f ->
                PredictionConfidence.LOW

            else ->
                PredictionConfidence.VERY_LOW
        }

    private fun sourceReliability(
        source: PredictionSource
    ): Float =
        when (source) {

            PredictionSource.USER_FEEDBACK ->
                1.0f

            PredictionSource.CURRENT_CONTEXT ->
                0.95f

            PredictionSource.CALENDAR ->
                0.92f

            PredictionSource.CONVERSATION ->
                0.90f

            PredictionSource.PERSONAL_KNOWLEDGE ->
                0.88f

            PredictionSource.ACTION_HISTORY ->
                0.82f

            PredictionSource.MEMORY ->
                0.80f

            PredictionSource.TEMPORAL_PATTERN ->
                0.78f

            PredictionSource.DEVICE_STATE ->
                0.75f

            PredictionSource.VISION ->
                0.70f

            PredictionSource.INFERENCE ->
                0.55f
        }
}
