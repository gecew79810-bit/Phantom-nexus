package com.pantham.nexus.decision.adapter

import com.pantham.nexus.decision.model.DecisionEvidence
import com.pantham.nexus.decision.model.DecisionOption
import com.pantham.nexus.decision.model.EvidenceType
import com.pantham.nexus.decision.parser.NexusDecisionQueryParser
import com.pantham.nexus.files.model.FileSearchResult
import com.pantham.nexus.intelligence.model.ResolvedEntity
import com.pantham.nexus.knowledge.KnowledgeEntity
import com.pantham.nexus.vision.model.VisualContext

class NexusDecisionOptionResolver(
    private val queryParser: NexusDecisionQueryParser = NexusDecisionQueryParser()
) {

    fun resolveFromQuery(query: String): List<DecisionOption> {
        val names = queryParser.extractOptionNames(query)
        if (names.size >= 2) {
            return names.mapIndexed { index, name ->
                DecisionOption(
                    id = "opt_${index + 1}",
                    name = name,
                    description = "Option extracted from user query: $name",
                    evidence = listOf(
                        DecisionEvidence(
                            type = EvidenceType.EXPLICIT_USER_FACT,
                            statement = "User explicitly mentioned '$name' in query",
                            reliability = 0.95f,
                            relevance = 1.0f
                        )
                    )
                )
            }
        }
        return emptyList()
    }

    fun resolveFromEntities(entities: List<ResolvedEntity>): List<DecisionOption> {
        return entities.take(10).mapIndexed { index, entity ->
            DecisionOption(
                id = entity.id.ifBlank { "entity_${index + 1}" },
                name = entity.name.ifBlank { entity.value }.ifBlank { "Entity ${index + 1}" },
                description = "Resolved ${entity.type} entity",
                attributes = if (entity.value.isNotBlank()) mapOf("value" to entity.value, "type" to entity.type) else mapOf("type" to entity.type),
                evidence = listOf(
                    DecisionEvidence(
                        type = EvidenceType.KNOWLEDGE,
                        sourceId = entity.id,
                        statement = "Resolved entity '${entity.name}' of type '${entity.type}'",
                        reliability = 0.85f,
                        relevance = 0.80f
                    )
                )
            )
        }
    }

    fun resolveFromFileResults(fileResults: List<FileSearchResult>): List<DecisionOption> {
        return fileResults.take(5).mapIndexed { index, file ->
            val ext = file.name.substringAfterLast('.', "")
            val snippet = file.snippet ?: file.matchedChunk?.text ?: ""
            DecisionOption(
                id = file.fileId,
                name = file.name,
                description = "Document: ${file.name}",
                attributes = mapOf(
                    "extension" to ext,
                    "uri" to file.uri,
                    "score" to file.matchScore.toString()
                ),
                evidence = listOf(
                    DecisionEvidence(
                        type = EvidenceType.FILE,
                        sourceId = file.fileId,
                        statement = "Document '${file.name}' snippet: $snippet",
                        reliability = (file.matchScore).coerceIn(0.6f, 0.95f),
                        relevance = 0.85f
                    )
                )
            )
        }
    }

    fun resolveFromKnowledge(knowledgeEntities: List<KnowledgeEntity>): List<DecisionOption> {
        return knowledgeEntities.take(5).map { kEntity ->
            DecisionOption(
                id = kEntity.id,
                name = kEntity.canonicalName,
                description = "Knowledge entity: ${kEntity.canonicalName} (${kEntity.type.name})",
                attributes = mapOf("entityType" to kEntity.type.name),
                evidence = listOf(
                    DecisionEvidence(
                        type = EvidenceType.KNOWLEDGE,
                        sourceId = kEntity.id,
                        statement = "Known entity '${kEntity.canonicalName}' in Personal Knowledge Core",
                        reliability = 0.90f,
                        relevance = 0.80f
                    )
                )
            )
        }
    }

    fun resolveFromVision(visualContext: VisualContext?): List<DecisionOption> {
        if (visualContext == null) return emptyList()

        val options = mutableListOf<DecisionOption>()
        visualContext.detectedObjects.take(4).forEachIndexed { idx, obj ->
            options += DecisionOption(
                id = "vision_obj_${idx + 1}",
                name = obj.label,
                description = "Visual object detected with confidence ${obj.confidence}",
                attributes = mapOf("confidence" to obj.confidence.toString()),
                evidence = listOf(
                    DecisionEvidence(
                        type = EvidenceType.VISION,
                        sourceId = visualContext.frameId,
                        statement = "Vision Core detected object '${obj.label}' with confidence ${obj.confidence}",
                        reliability = obj.confidence.coerceIn(0.4f, 0.9f),
                        relevance = 0.70f
                    )
                )
            )
        }
        return options
    }
}
