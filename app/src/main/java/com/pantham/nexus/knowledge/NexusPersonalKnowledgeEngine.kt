package com.pantham.nexus.knowledge

class NexusPersonalKnowledgeEngine(
    val repository: NexusKnowledgeRepository,
    val graph: NexusKnowledgeGraph
) {

    suspend fun rememberPerson(
        name: String,
        aliases: Set<String> = emptySet(),
        attributes: Map<String, String> = emptyMap()
    ): KnowledgeEntity {

        val existing =
            repository.findEntitiesByName(
                name,
                5
            ).firstOrNull {
                it.type ==
                    KnowledgeEntityType.PERSON
            }

        val entity =
            existing?.copy(
                aliases =
                    existing.aliases + aliases,
                attributes =
                    existing.attributes +
                        attributes,
                updatedAt =
                    System.currentTimeMillis()
            )
                ?: KnowledgeEntity(
                    type =
                        KnowledgeEntityType.PERSON,
                    canonicalName =
                        name.trim(),
                    aliases =
                        aliases,
                    attributes =
                        attributes,
                    source =
                        KnowledgeSource.USER_EXPLICIT,
                    confidence =
                        KnowledgeConfidence.VERY_HIGH
                )

        repository.upsertEntity(entity)

        return entity
    }

    suspend fun rememberFact(
        entity: KnowledgeEntity,
        predicate: String,
        value: String,
        source: KnowledgeSource =
            KnowledgeSource.USER_EXPLICIT,
        confidence: KnowledgeConfidence =
            KnowledgeConfidence.HIGH,
        evidence: String? = null
    ) {

        repository.addObservation(
            KnowledgeObservation(
                subjectEntityId =
                    entity.id,
                predicate =
                    predicate,
                value =
                    value,
                source =
                    source,
                confidence =
                    confidence,
                evidence =
                    evidence
            )
        )
    }

    suspend fun relate(
        from: KnowledgeEntity,
        relationshipType: RelationshipType,
        to: KnowledgeEntity,
        source: KnowledgeSource =
            KnowledgeSource.USER_EXPLICIT,
        confidence: KnowledgeConfidence =
            KnowledgeConfidence.HIGH,
        evidence: String? = null
    ) {

        graph.connect(
            from = from,
            relationshipType =
                relationshipType,
            to = to,
            source = source,
            confidence =
                confidence,
            evidence = evidence
        )
    }

    suspend fun search(
        query: String,
        type: KnowledgeEntityType? = null
    ): List<KnowledgeSearchResult> {

        return repository.searchEntities(
            KnowledgeQuery(
                text = query,
                entityTypes =
                    if (type != null)
                        setOf(type)
                    else
                        emptySet()
            )
        )
    }

    suspend fun getPersonContext(
        name: String
    ): KnowledgeContext {

        val matches =
            search(
                name,
                KnowledgeEntityType.PERSON
            )

        val person =
            matches.firstOrNull()
                ?.entity
                ?: return KnowledgeContext(
                    relevantEntities =
                        emptyList(),
                    relevantRelationships =
                        emptyList(),
                    relevantObservations =
                        emptyList()
                )

        val relationships =
            repository.findRelationships(
                person.id
            )

        val observations =
            repository.getObservations(
                person.id
            )

        val relatedEntities =
            relationships
                .flatMap {
                    listOf(
                        it.fromEntityId,
                        it.toEntityId
                    )
                }
                .distinct()
                .filter {
                    it != person.id
                }
                .mapNotNull {
                    repository.getEntity(it)
                }

        return KnowledgeContext(
            relevantEntities =
                listOf(person) +
                    relatedEntities,
            relevantRelationships =
                relationships,
            relevantObservations =
                observations
        )
    }

    suspend fun explainConnection(
        firstName: String,
        secondName: String
    ): RelationshipPath? {

        val first =
            search(firstName)
                .firstOrNull()
                ?.entity
                ?: return null

        val second =
            search(secondName)
                .firstOrNull()
                ?.entity
                ?: return null

        return graph.findPath(
            startEntityId = first.id,
            targetEntityId = second.id
        )
    }

    suspend fun detectConflicts(
        entityId: String
    ): List<KnowledgeConflict> {

        return repository.findConflictingFacts(
            entityId
        )
    }
}
