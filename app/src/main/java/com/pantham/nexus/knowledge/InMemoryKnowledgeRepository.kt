package com.pantham.nexus.knowledge

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryKnowledgeRepository : NexusKnowledgeRepository {

    private val mutex = Mutex()

    private val entities =
        LinkedHashMap<String, KnowledgeEntity>()

    private val relationships =
        LinkedHashMap<String, KnowledgeRelationship>()

    private val observations =
        LinkedHashMap<String, KnowledgeObservation>()

    override suspend fun upsertEntity(entity: KnowledgeEntity) {
        mutex.withLock {
            entities[entity.id] = entity
        }
    }

    override suspend fun getEntity(
        id: String
    ): KnowledgeEntity? =
        mutex.withLock {
            entities[id]
        }

    override suspend fun findEntitiesByName(
        name: String,
        limit: Int
    ): List<KnowledgeEntity> {

        val normalized = normalize(name)

        return mutex.withLock {
            entities.values
                .filter { entity ->
                    normalize(entity.canonicalName).contains(normalized) ||
                    entity.aliases.any {
                        normalize(it).contains(normalized)
                    }
                }
                .take(limit)
        }
    }

    override suspend fun searchEntities(
        query: KnowledgeQuery
    ): List<KnowledgeSearchResult> {

        val normalized = normalize(query.text)

        return mutex.withLock {

            entities.values
                .asSequence()
                .filter { entity ->

                    entity.active &&
                    (
                        query.entityTypes.isEmpty() ||
                        entity.type in query.entityTypes
                    )
                }
                .mapNotNull { entity ->

                    val nameScore =
                        similarity(
                            normalized,
                            normalize(entity.canonicalName)
                        )

                    val aliasMatch =
                        entity.aliases
                            .map {
                                normalize(it) to similarity(
                                    normalized,
                                    normalize(it)
                                )
                            }
                            .maxByOrNull { it.second }

                    val attributeMatch =
                        entity.attributes
                            .entries
                            .map {
                                "${it.key}:${it.value}" to similarity(
                                    normalized,
                                    normalize(
                                        "${it.key} ${it.value}"
                                    )
                                )
                            }
                            .maxByOrNull { it.second }

                    val best =
                        listOfNotNull(
                            nameScore,
                            aliasMatch?.second,
                            attributeMatch?.second
                        ).maxOrNull() ?: 0f

                    if (best < 0.15f) {
                        null
                    } else {

                        KnowledgeSearchResult(
                            entity = entity,
                            score = best,
                            matchedAlias = aliasMatch?.first,
                            matchedAttributes =
                                attributeMatch
                                    ?.first
                                    ?.let { listOf(it) }
                                    ?: emptyList()
                        )
                    }
                }
                .sortedByDescending { it.score }
                .take(query.maxResults)
                .toList()
        }
    }

    override suspend fun deactivateEntity(
        entityId: String
    ) {
        mutex.withLock {
            entities[entityId]?.let {
                entities[entityId] =
                    it.copy(active = false)
            }
        }
    }

    override suspend fun upsertRelationship(
        relationship: KnowledgeRelationship
    ) {
        mutex.withLock {
            relationships[relationship.id] =
                relationship
        }
    }

    override suspend fun getRelationship(
        id: String
    ): KnowledgeRelationship? =
        mutex.withLock {
            relationships[id]
        }

    override suspend fun findRelationshipsFrom(
        entityId: String
    ): List<KnowledgeRelationship> =
        mutex.withLock {
            relationships.values.filter {
                it.active &&
                it.fromEntityId == entityId
            }
        }

    override suspend fun findRelationshipsTo(
        entityId: String
    ): List<KnowledgeRelationship> =
        mutex.withLock {
            relationships.values.filter {
                it.active &&
                it.toEntityId == entityId
            }
        }

    override suspend fun findRelationships(
        entityId: String,
        type: RelationshipType?
    ): List<KnowledgeRelationship> =
        mutex.withLock {
            relationships.values.filter {

                it.active &&
                (
                    it.fromEntityId == entityId ||
                    it.toEntityId == entityId
                ) &&
                (
                    type == null ||
                    it.relationshipType == type
                )
            }
        }

    override suspend fun deleteRelationship(
        relationshipId: String
    ) {
        mutex.withLock {
            relationships.remove(relationshipId)
        }
    }

    override suspend fun addObservation(
        observation: KnowledgeObservation
    ) {
        mutex.withLock {
            observations[observation.id] =
                observation
        }
    }

    override suspend fun getObservations(
        entityId: String,
        limit: Int
    ): List<KnowledgeObservation> =
        mutex.withLock {
            observations.values
                .filter {
                    it.subjectEntityId == entityId
                }
                .sortedByDescending {
                    it.timestamp
                }
                .take(limit)
        }

    override suspend fun findObservations(
        predicate: String,
        value: String?,
        limit: Int
    ): List<KnowledgeObservation> =
        mutex.withLock {
            observations.values
                .filter {
                    it.predicate.equals(
                        predicate,
                        ignoreCase = true
                    ) &&
                    (
                        value == null ||
                        it.value.contains(
                            value,
                            ignoreCase = true
                        )
                    )
                }
                .sortedByDescending {
                    it.timestamp
                }
                .take(limit)
        }

    override suspend fun getRecentEntities(
        limit: Int
    ): List<KnowledgeEntity> =
        mutex.withLock {
            entities.values
                .sortedByDescending {
                    it.updatedAt
                }
                .take(limit)
        }

    override suspend fun getImportantEntities(
        limit: Int
    ): List<KnowledgeEntity> =
        mutex.withLock {
            entities.values
                .sortedByDescending {
                    it.importance
                }
                .take(limit)
        }

    override suspend fun findConflictingFacts(
        entityId: String
    ): List<KnowledgeConflict> {

        val facts =
            getObservations(entityId, 100)

        return facts
            .groupBy {
                it.predicate
            }
            .flatMap { (_, group) ->

                val values =
                    group
                        .map { it.value }
                        .distinct()

                if (values.size <= 1) {
                    emptyList()
                } else {

                    values
                        .drop(1)
                        .map { newValue ->

                            KnowledgeConflict(
                                key =
                                    group.first().predicate,
                                existingValue =
                                    values.first(),
                                newValue =
                                    newValue,
                                existingSource =
                                    group.first().source,
                                newSource =
                                    group.firstOrNull {
                                        it.value == newValue
                                    }?.source
                                        ?: KnowledgeSource.INFERRED
                            )
                        }
                }
            }
    }

    override suspend fun clearAll() {
        mutex.withLock {
            entities.clear()
            relationships.clear()
            observations.clear()
        }
    }

    private fun normalize(
        value: String
    ): String =
        value
            .trim()
            .lowercase()
            .replace(
                Regex("\\s+"),
                " "
            )

    private fun similarity(
        a: String,
        b: String
    ): Float {

        if (a.isBlank() || b.isBlank()) {
            return 0f
        }

        if (a == b) {
            return 1f
        }

        if (a.contains(b) || b.contains(a)) {
            return 0.85f
        }

        val aTokens =
            a.split(" ").toSet()

        val bTokens =
            b.split(" ").toSet()

        if (aTokens.isEmpty() || bTokens.isEmpty()) {
            return 0f
        }

        val intersection =
            aTokens.intersect(bTokens).size

        val union =
            aTokens.union(bTokens).size

        return intersection
            .toFloat()
            .div(union)
    }
}
