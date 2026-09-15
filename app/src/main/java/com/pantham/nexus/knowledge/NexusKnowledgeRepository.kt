package com.pantham.nexus.knowledge

interface NexusKnowledgeRepository {

    suspend fun upsertEntity(entity: KnowledgeEntity)

    suspend fun getEntity(id: String): KnowledgeEntity?

    suspend fun findEntitiesByName(
        name: String,
        limit: Int = 20
    ): List<KnowledgeEntity>

    suspend fun searchEntities(
        query: KnowledgeQuery
    ): List<KnowledgeSearchResult>

    suspend fun deactivateEntity(
        entityId: String
    )

    suspend fun upsertRelationship(
        relationship: KnowledgeRelationship
    )

    suspend fun getRelationship(
        id: String
    ): KnowledgeRelationship?

    suspend fun findRelationshipsFrom(
        entityId: String
    ): List<KnowledgeRelationship>

    suspend fun findRelationshipsTo(
        entityId: String
    ): List<KnowledgeRelationship>

    suspend fun findRelationships(
        entityId: String,
        type: RelationshipType? = null
    ): List<KnowledgeRelationship>

    suspend fun deleteRelationship(
        relationshipId: String
    )

    suspend fun addObservation(
        observation: KnowledgeObservation
    )

    suspend fun getObservations(
        entityId: String,
        limit: Int = 50
    ): List<KnowledgeObservation>

    suspend fun findObservations(
        predicate: String,
        value: String? = null,
        limit: Int = 50
    ): List<KnowledgeObservation>

    suspend fun getRecentEntities(
        limit: Int = 50
    ): List<KnowledgeEntity>

    suspend fun getImportantEntities(
        limit: Int = 50
    ): List<KnowledgeEntity>

    suspend fun findConflictingFacts(
        entityId: String
    ): List<KnowledgeConflict>

    suspend fun clearAll()
}
