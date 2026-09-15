package com.pantham.nexus.knowledge

class NexusKnowledgeIngestionEngine(
    private val repository: NexusKnowledgeRepository,
    private val graph: NexusKnowledgeGraph,
    private val extractor: NexusKnowledgeExtractor
) {

    suspend fun ingestConversation(
        text: String
    ): KnowledgeContext {

        val extraction =
            extractor.extract(text)

        val entityMap =
            mutableMapOf<String, KnowledgeEntity>()

        for (item in extraction.entities) {

            val existing =
                repository.findEntitiesByName(
                    item.name,
                    5
                ).firstOrNull()

            val entity =
                existing?.copy(
                    aliases =
                        existing.aliases +
                            item.aliases,

                    attributes =
                        existing.attributes +
                            item.attributes,

                    updatedAt =
                        System.currentTimeMillis(),

                    lastMentionedAt =
                        System.currentTimeMillis()
                )
                    ?: KnowledgeEntity(
                        type = item.type,
                        canonicalName = item.name,
                        aliases = item.aliases,
                        attributes = item.attributes,
                        source = KnowledgeSource.CONVERSATION,
                        confidence =
                            KnowledgeConfidence.MEDIUM,
                        lastMentionedAt =
                            System.currentTimeMillis()
                    )

            repository.upsertEntity(entity)

            entityMap[
                item.name.lowercase()
            ] = entity
        }

        for (relationship in extraction.relationships) {

            val from =
                entityMap[
                    relationship.from.lowercase()
                ]

            val to =
                entityMap[
                    relationship.to.lowercase()
                ]

            if (from != null && to != null) {

                graph.connect(
                    from = from,
                    relationshipType =
                        relationship.type,
                    to = to,
                    source =
                        KnowledgeSource.CONVERSATION,
                    confidence =
                        relationship.confidence,
                    evidence = text
                )
            }
        }

        for (observation in extraction.observations) {

            val subject =
                if (
                    observation.subject
                        .equals(
                            "USER",
                            ignoreCase = true
                        )
                ) {
                    getOrCreateUserEntity()
                } else {
                    entityMap[
                        observation.subject.lowercase()
                    ]
                }

            if (subject != null) {

                repository.addObservation(
                    KnowledgeObservation(
                        subjectEntityId =
                            subject.id,
                        predicate =
                            observation.predicate,
                        value =
                            observation.value,
                        source =
                            KnowledgeSource.CONVERSATION,
                        confidence =
                            KnowledgeConfidence.HIGH,
                        evidence = text
                    )
                )
            }
        }

        return buildContext(
            extraction.entities
                .mapNotNull {
                    entityMap[
                        it.name.lowercase()
                    ]
                }
        )
    }

    private suspend fun getOrCreateUserEntity():
        KnowledgeEntity {

        val existing =
            repository.findEntitiesByName(
                "USER",
                1
            ).firstOrNull()

        if (existing != null) {
            return existing
        }

        return KnowledgeEntity(
            type = KnowledgeEntityType.PERSON,
            canonicalName = "USER",
            aliases =
                setOf(
                    "me",
                    "myself",
                    "user"
                ),
            source =
                KnowledgeSource.USER_EXPLICIT,
            confidence =
                KnowledgeConfidence.VERY_HIGH,
            importance = 1f
        ).also {
            repository.upsertEntity(it)
        }
    }

    private suspend fun buildContext(
        entities: List<KnowledgeEntity>
    ): KnowledgeContext {

        val relationships =
            entities.flatMap {
                repository.findRelationships(
                    it.id
                )
            }.distinctBy {
                it.id
            }

        val observations =
            entities.flatMap {
                repository.getObservations(
                    it.id
                )
            }

        return KnowledgeContext(
            relevantEntities = entities,
            relevantRelationships =
                relationships,
            relevantObservations =
                observations
        )
    }
}
