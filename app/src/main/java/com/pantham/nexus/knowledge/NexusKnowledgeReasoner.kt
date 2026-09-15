package com.pantham.nexus.knowledge

class NexusKnowledgeReasoner(
    private val engine: NexusPersonalKnowledgeEngine
) {

    suspend fun answer(
        question: String
    ): KnowledgeAnswer {

        val matches =
            engine.search(question)

        if (matches.isEmpty()) {

            return KnowledgeAnswer(
                answer =
                    "मुझे इस सवाल से जुड़ी personal knowledge नहीं मिली।",
                confidence =
                    KnowledgeConfidence.VERY_LOW
            )
        }

        val top =
            matches.first().entity

        val context =
            when (
                top.type
            ) {

                KnowledgeEntityType.PERSON ->
                    engine.getPersonContext(
                        top.canonicalName
                    )

                else ->
                    KnowledgeContext(
                        relevantEntities =
                            listOf(top),
                        relevantRelationships =
                            emptyList(),
                        relevantObservations =
                            emptyList()
                    )
            }

        val answer =
            buildNaturalAnswer(
                question,
                context
            )

        return KnowledgeAnswer(
            answer = answer,
            entities =
                context.relevantEntities,
            relationships =
                context.relevantRelationships,
            evidence =
                context.relevantObservations
                    .mapNotNull {
                        it.evidence
                    },
            confidence =
                if (
                    context.relevantEntities
                        .isNotEmpty()
                ) {
                    KnowledgeConfidence.HIGH
                } else {
                    KnowledgeConfidence.LOW
                }
        )
    }

    private fun buildNaturalAnswer(
        question: String,
        context: KnowledgeContext
    ): String {

        val person =
            context.relevantEntities
                .firstOrNull {
                    it.type ==
                        KnowledgeEntityType.PERSON
                }

        if (person == null) {
            return "मुझे ${context.relevantEntities.firstOrNull()?.canonicalName ?: "इस विषय"} से संबंधित जानकारी मिली है।"
        }

        val facts =
            context.relevantObservations
                .take(5)
                .joinToString("; ") {
                    "${it.predicate}: ${it.value}"
                }

        val relationships =
            context.relevantRelationships
                .take(5)
                .joinToString("; ") {
                    it.relationshipType.name
                        .lowercase()
                        .replace("_", " ")
                }

        return buildString {

            append(person.canonicalName)

            if (facts.isNotBlank()) {
                append(" — ")
                append(facts)
            }

            if (relationships.isNotBlank()) {
                append(". संबंध: ")
                append(relationships)
            }

            append(".")
        }
    }
}
