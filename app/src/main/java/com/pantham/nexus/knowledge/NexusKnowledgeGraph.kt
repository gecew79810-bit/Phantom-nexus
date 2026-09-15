package com.pantham.nexus.knowledge

import java.util.ArrayDeque

class NexusKnowledgeGraph(
    private val repository: NexusKnowledgeRepository
) {

    suspend fun connect(
        from: KnowledgeEntity,
        relationshipType: RelationshipType,
        to: KnowledgeEntity,
        source: KnowledgeSource,
        confidence: KnowledgeConfidence = KnowledgeConfidence.MEDIUM,
        evidence: String? = null
    ) {

        repository.upsertEntity(from)
        repository.upsertEntity(to)

        repository.upsertRelationship(
            KnowledgeRelationship(
                fromEntityId = from.id,
                relationshipType = relationshipType,
                toEntityId = to.id,
                source = source,
                confidence = confidence,
                evidence = evidence
            )
        )
    }

    suspend fun neighbors(
        entityId: String,
        relationshipType: RelationshipType? = null
    ): List<KnowledgeEntity> {

        val relationships =
            repository.findRelationships(
                entityId,
                relationshipType
            )

        val ids =
            relationships.flatMap {

                listOf(
                    it.fromEntityId,
                    it.toEntityId
                )
            }
            .filter {
                it != entityId
            }
            .distinct()

        return ids.mapNotNull {
            repository.getEntity(it)
        }
    }

    suspend fun findPath(
        startEntityId: String,
        targetEntityId: String,
        maxDepth: Int = 4
    ): RelationshipPath? {

        if (startEntityId == targetEntityId) {

            val entity =
                repository.getEntity(startEntityId)
                    ?: return null

            return RelationshipPath(
                entities = listOf(entity),
                relationships = emptyList(),
                score = 1f
            )
        }

        data class Node(
            val entityId: String,
            val entities: List<KnowledgeEntity>,
            val relationships: List<KnowledgeRelationship>,
            val depth: Int
        )

        val queue =
            ArrayDeque<Node>()

        val visited =
            mutableSetOf<String>()

        val start =
            repository.getEntity(startEntityId)
                ?: return null

        queue.add(
            Node(
                entityId = startEntityId,
                entities = listOf(start),
                relationships = emptyList(),
                depth = 0
            )
        )

        visited += startEntityId

        while (queue.isNotEmpty()) {

            val node =
                queue.removeFirst()

            if (node.depth >= maxDepth) {
                continue
            }

            val relationships =
                repository.findRelationships(
                    node.entityId
                )

            for (relationship in relationships) {

                val nextId =
                    if (
                        relationship.fromEntityId ==
                        node.entityId
                    ) {
                        relationship.toEntityId
                    } else {
                        relationship.fromEntityId
                    }

                if (nextId in visited) {
                    continue
                }

                val next =
                    repository.getEntity(nextId)
                        ?: continue

                val nextEntities =
                    node.entities + next

                val nextRelationships =
                    node.relationships + relationship

                if (nextId == targetEntityId) {

                    val score =
                        1f /
                            nextEntities.size
                                .toFloat()

                    return RelationshipPath(
                        entities =
                            nextEntities,
                        relationships =
                            nextRelationships,
                        score = score
                    )
                }

                visited += nextId

                queue.add(
                    Node(
                        entityId = nextId,
                        entities = nextEntities,
                        relationships =
                            nextRelationships,
                        depth =
                            node.depth + 1
                    )
                )
            }
        }

        return null
    }
}
