package com.pantham.nexus.knowledge

class NexusKnowledgeConfidenceEngine {

    fun merge(
        oldConfidence: KnowledgeConfidence,
        newConfidence: KnowledgeConfidence,
        source: KnowledgeSource
    ): KnowledgeConfidence {

        val old =
            score(oldConfidence)

        val incoming =
            score(newConfidence)

        val sourceWeight =
            when (source) {

                KnowledgeSource.USER_EXPLICIT ->
                    1.0f

                KnowledgeSource.CONVERSATION ->
                    0.9f

                KnowledgeSource.CONTACT,
                KnowledgeSource.CALENDAR ->
                    0.85f

                KnowledgeSource.FILE,
                KnowledgeSource.NOTIFICATION ->
                    0.75f

                KnowledgeSource.VISION ->
                    0.7f

                KnowledgeSource.OBSERVATION ->
                    0.65f

                KnowledgeSource.ACTION_HISTORY ->
                    0.65f

                KnowledgeSource.IMPORTED ->
                    0.6f

                KnowledgeSource.INFERRED ->
                    0.4f
            }

        val merged =
            (
                old * 0.4f +
                    incoming *
                    sourceWeight *
                    0.6f
                ).coerceIn(0f, 1f)

        return fromScore(merged)
    }

    private fun score(
        confidence: KnowledgeConfidence
    ): Float =
        when (confidence) {

            KnowledgeConfidence.VERY_LOW ->
                0.1f

            KnowledgeConfidence.LOW ->
                0.3f

            KnowledgeConfidence.MEDIUM ->
                0.5f

            KnowledgeConfidence.HIGH ->
                0.75f

            KnowledgeConfidence.VERY_HIGH ->
                0.95f
        }

    private fun fromScore(
        value: Float
    ): KnowledgeConfidence =
        when {

            value >= 0.9f ->
                KnowledgeConfidence.VERY_HIGH

            value >= 0.7f ->
                KnowledgeConfidence.HIGH

            value >= 0.45f ->
                KnowledgeConfidence.MEDIUM

            value >= 0.2f ->
                KnowledgeConfidence.LOW

            else ->
                KnowledgeConfidence.VERY_LOW
        }
}
