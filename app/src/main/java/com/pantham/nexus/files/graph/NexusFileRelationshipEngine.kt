package com.pantham.nexus.files.graph

import com.pantham.nexus.files.model.ExtractedEntity
import com.pantham.nexus.files.model.SemanticFileIndexItem
import com.pantham.nexus.knowledge.KnowledgeConfidence
import com.pantham.nexus.knowledge.KnowledgeEntity
import com.pantham.nexus.knowledge.KnowledgeEntityType
import com.pantham.nexus.knowledge.KnowledgeRelationship
import com.pantham.nexus.knowledge.NexusKnowledgeRepository
import com.pantham.nexus.knowledge.KnowledgeSource
import com.pantham.nexus.knowledge.RelationshipType
import java.util.UUID

data class FileRelationshipCandidate(
    val fileId: String,
    val entityText: String,
    val relationshipType: String,
    val confidence: Float,
    val evidence: String
)

interface ExistingKnowledgeFileBridge {

    suspend fun createCandidateRelationship(
        fileId: String,
        entityText: String,
        relationshipType: String,
        confidence: Float,
        evidence: String
    )
}

class DefaultKnowledgeFileBridge(
    private val repository: NexusKnowledgeRepository?
) : ExistingKnowledgeFileBridge {

    override suspend fun createCandidateRelationship(
        fileId: String,
        entityText: String,
        relationshipType: String,
        confidence: Float,
        evidence: String
    ) {
        val repo = repository ?: return

        // 1. Upsert file entity
        val fileEntityId = "file_$fileId"
        val fileEntity = KnowledgeEntity(
            id = fileEntityId,
            type = KnowledgeEntityType.FILE,
            canonicalName = fileId,
            source = KnowledgeSource.FILE,
            confidence = KnowledgeConfidence.HIGH
        )
        repo.upsertEntity(fileEntity)

        // 2. Find or create target entity
        val candidates = repo.findEntitiesByName(entityText, limit = 5)
        val targetEntity = candidates.firstOrNull() ?: run {
            val isPerson = relationshipType == "CREATED_BY"
            val newEntity = KnowledgeEntity(
                id = UUID.randomUUID().toString(),
                type = if (isPerson) KnowledgeEntityType.PERSON else KnowledgeEntityType.CONCEPT,
                canonicalName = entityText,
                source = KnowledgeSource.FILE,
                confidence = KnowledgeConfidence.MEDIUM
            )
            repo.upsertEntity(newEntity)
            newEntity
        }

        // 3. Upsert relationship
        val relType = try {
            RelationshipType.valueOf(relationshipType)
        } catch (_: Throwable) {
            RelationshipType.RELATED_TO
        }

        val relationship = KnowledgeRelationship(
            id = UUID.randomUUID().toString(),
            fromEntityId = targetEntity.id,
            relationshipType = relType,
            toEntityId = fileEntity.id,
            source = KnowledgeSource.FILE,
            confidence = if (confidence >= 0.8f) KnowledgeConfidence.HIGH else KnowledgeConfidence.MEDIUM,
            evidence = evidence
        )
        repo.upsertRelationship(relationship)
    }
}

class NexusFileRelationshipEngine(
    private val knowledgeBridge:
        ExistingKnowledgeFileBridge?
) {

    suspend fun buildCandidates(
        item: SemanticFileIndexItem
    ): List<FileRelationshipCandidate> {

        val results =
            mutableListOf<FileRelationshipCandidate>()

        for (entity in item.extractedEntities) {

            val type =
                when {
                    entity.type.equals(
                        "PERSON",
                        true
                    ) -> "CREATED_BY"

                    else -> "MENTIONED_IN"
                }

            results +=
                FileRelationshipCandidate(
                    fileId =
                        item.metadata.fileId,
                    entityText =
                        entity.text,
                    relationshipType =
                        type,
                    confidence =
                        entity.confidence
                            .coerceIn(0f, 1f),
                    evidence =
                        "Entity extracted from indexed file content."
                )
        }

        return results
    }

    suspend fun publish(
        candidates:
            List<FileRelationshipCandidate>
    ) {

        val bridge =
            knowledgeBridge
                ?: return

        for (candidate in candidates) {

            try {

                bridge.createCandidateRelationship(
                    fileId =
                        candidate.fileId,
                    entityText =
                        candidate.entityText,
                    relationshipType =
                        candidate.relationshipType,
                    confidence =
                        candidate.confidence,
                    evidence =
                        candidate.evidence
                )

            } catch (_: Throwable) {
                // Knowledge failure must not break file intelligence.
            }
        }
    }
}
