package com.pantham.nexus.knowledge

/**
 * Adapter contract.
 *
 * IMPORTANT:
 * Do not create a second independent database if Nexus
 * already has a canonical SQLCipher/Room memory database.
 *
 * Implement this against the existing encrypted persistence layer.
 */
interface NexusKnowledgeStorageAdapter {

    suspend fun saveEntity(
        entity: KnowledgeEntity
    )

    suspend fun loadEntity(
        id: String
    ): KnowledgeEntity?

    suspend fun searchEntities(
        query: String,
        limit: Int
    ): List<KnowledgeEntity>

    suspend fun saveRelationship(
        relationship: KnowledgeRelationship
    )

    suspend fun loadRelationships(
        entityId: String
    ): List<KnowledgeRelationship>

    suspend fun saveObservation(
        observation: KnowledgeObservation
    )

    suspend fun loadObservations(
        entityId: String,
        limit: Int
    ): List<KnowledgeObservation>
}
