package com.pantham.nexus.knowledge

class NexusKnowledgeExtractor {

    data class Extraction(
        val entities: List<ExtractedEntity> = emptyList(),
        val relationships: List<ExtractedRelationship> = emptyList(),
        val observations: List<ExtractedObservation> = emptyList()
    )

    data class ExtractedEntity(
        val name: String,
        val type: KnowledgeEntityType,
        val aliases: Set<String> = emptySet(),
        val attributes: Map<String, String> = emptyMap()
    )

    data class ExtractedRelationship(
        val from: String,
        val type: RelationshipType,
        val to: String,
        val confidence: KnowledgeConfidence =
            KnowledgeConfidence.MEDIUM
    )

    data class ExtractedObservation(
        val subject: String,
        val predicate: String,
        val value: String
    )

    /**
     * Lightweight deterministic extraction.
     *
     * Production implementation should additionally
     * receive structured extraction from the project's
     * existing LLM/intent layer.
     */
    fun extract(
        text: String
    ): Extraction {

        val entities =
            mutableListOf<ExtractedEntity>()

        val relationships =
            mutableListOf<ExtractedRelationship>()

        val observations =
            mutableListOf<ExtractedObservation>()

        val normalized =
            text.trim()

        if (normalized.isBlank()) {
            return Extraction()
        }

        /*
         * Do NOT blindly treat every capitalized token as a person.
         * This layer intentionally uses conservative patterns.
         */

        val relationPatterns =
            listOf(
                Regex(
                    """(?i)([A-Za-z][A-Za-z0-9 _-]{1,40})\s+(?:is|works)\s+(?:my\s+)?(?:friend|colleague|brother|sister|manager|boss)"""
                ),
                Regex(
                    """(?i)(?:my\s+)?(?:friend|colleague|brother|sister|manager|boss)\s+(?:is|named|called)\s+([A-Za-z][A-Za-z0-9 _-]{1,40})"""
                )
            )

        for (pattern in relationPatterns) {

            val match =
                pattern.find(normalized)
                    ?: continue

            val candidate =
                match.groupValues
                    .drop(1)
                    .firstOrNull()
                    ?.trim()
                    ?: continue

            if (candidate.isBlank()) {
                continue
            }

            entities +=
                ExtractedEntity(
                    name = candidate,
                    type = KnowledgeEntityType.PERSON
                )
        }

        /*
         * Preference extraction.
         */

        val preference =
            Regex(
                """(?i)(?:i|user)\s+(?:like|love|prefer)\s+(.+)"""
            ).find(normalized)

        if (preference != null) {

            val value =
                preference.groupValues[1]
                    .trim()
                    .trimEnd('.', '!', '?')

            if (value.isNotBlank()) {

                observations +=
                    ExtractedObservation(
                        subject = "USER",
                        predicate = "preference",
                        value = value
                    )
            }
        }

        return Extraction(
            entities = entities.distinctBy {
                it.name.lowercase()
            },
            relationships = relationships,
            observations = observations
        )
    }
}
