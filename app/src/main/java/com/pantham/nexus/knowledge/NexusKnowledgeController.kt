package com.pantham.nexus.knowledge

class NexusKnowledgeController(
    private val repository: NexusKnowledgeRepository
) {

    private val graph =
        NexusKnowledgeGraph(
            repository
        )

    private val extractor =
        NexusKnowledgeExtractor()

    private val ingestion =
        NexusKnowledgeIngestionEngine(
            repository = repository,
            graph = graph,
            extractor = extractor
        )

    val personalEngine =
        NexusPersonalKnowledgeEngine(
            repository = repository,
            graph = graph
        )

    val reasoner =
        NexusKnowledgeReasoner(
            personalEngine
        )

    suspend fun processConversation(
        text: String
    ): KnowledgeContext {

        return ingestion.ingestConversation(
            text
        )
    }

    suspend fun ask(
        question: String
    ): KnowledgeAnswer {

        return reasoner.answer(
            question
        )
    }

    suspend fun rememberPerson(
        name: String,
        aliases: Set<String> = emptySet(),
        attributes: Map<String, String> = emptyMap()
    ): KnowledgeEntity {

        return personalEngine.rememberPerson(
            name = name,
            aliases = aliases,
            attributes = attributes
        )
    }

    suspend fun connectPeople(
        first: KnowledgeEntity,
        relation: RelationshipType,
        second: KnowledgeEntity
    ) {

        personalEngine.relate(
            from = first,
            relationshipType = relation,
            to = second
        )
    }

    suspend fun explainConnection(
        first: String,
        second: String
    ): RelationshipPath? {

        return personalEngine.explainConnection(
            first,
            second
        )
    }
}
