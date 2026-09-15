package com.pantham.nexus.knowledge

import java.util.UUID

enum class KnowledgeEntityType {
    PERSON,
    PLACE,
    ORGANIZATION,
    APP,
    DEVICE,
    PROJECT,
    FILE,
    EVENT,
    TASK,
    CONCEPT,
    OBJECT,
    ACCOUNT,
    OTHER
}

enum class RelationshipType {
    KNOWS,
    FAMILY_OF,
    FRIEND_OF,
    COLLEAGUE_OF,
    WORKS_WITH,
    WORKS_AT,
    MANAGES,
    OWNS,
    MEMBER_OF,
    LIVES_IN,
    FROM,
    RELATED_TO,
    DEPENDS_ON,
    CREATED_BY,
    USED_WITH,
    PART_OF,
    ATTENDED,
    MENTIONED_IN,
    ASSOCIATED_WITH,
    PREFERS,
    DISLIKES,
    INTERESTED_IN,
    WORKING_ON,
    RESPONSIBLE_FOR,
    HAS_TASK,
    HAS_EVENT,
    HAS_FILE,
    CUSTOM
}

enum class KnowledgeSource {
    USER_EXPLICIT,
    CONVERSATION,
    OBSERVATION,
    FILE,
    NOTIFICATION,
    CALENDAR,
    CONTACT,
    VISION,
    ACTION_HISTORY,
    IMPORTED,
    INFERRED
}

enum class KnowledgeConfidence {
    VERY_LOW,
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}

data class KnowledgeEntity(
    val id: String = UUID.randomUUID().toString(),
    val type: KnowledgeEntityType,
    val canonicalName: String,
    val aliases: Set<String> = emptySet(),
    val description: String? = null,

    /**
     * Arbitrary structured attributes.
     * Example:
     * phone = ...
     * favoriteColor = ...
     * company = ...
     */
    val attributes: Map<String, String> = emptyMap(),

    val source: KnowledgeSource,
    val confidence: KnowledgeConfidence = KnowledgeConfidence.MEDIUM,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    val lastMentionedAt: Long? = null,
    val lastObservedAt: Long? = null,

    val importance: Float = 0.5f,
    val active: Boolean = true
)

data class KnowledgeRelationship(
    val id: String = UUID.randomUUID().toString(),

    val fromEntityId: String,
    val relationshipType: RelationshipType,
    val toEntityId: String,

    val attributes: Map<String, String> = emptyMap(),

    val source: KnowledgeSource,
    val confidence: KnowledgeConfidence = KnowledgeConfidence.MEDIUM,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    val evidence: String? = null,

    val active: Boolean = true
)

data class KnowledgeObservation(
    val id: String = UUID.randomUUID().toString(),

    val subjectEntityId: String,

    val predicate: String,

    val value: String,

    val source: KnowledgeSource,

    val confidence: KnowledgeConfidence = KnowledgeConfidence.MEDIUM,

    val timestamp: Long = System.currentTimeMillis(),

    val evidence: String? = null
)

data class KnowledgeQuery(
    val text: String,

    val entityTypes: Set<KnowledgeEntityType> = emptySet(),

    val relationshipTypes: Set<RelationshipType> = emptySet(),

    val maxResults: Int = 20,

    val minimumConfidence: KnowledgeConfidence = KnowledgeConfidence.VERY_LOW
)

data class KnowledgeSearchResult(
    val entity: KnowledgeEntity,
    val score: Float,

    val matchedAlias: String? = null,

    val matchedAttributes: List<String> = emptyList()
)

data class RelationshipPath(
    val entities: List<KnowledgeEntity>,
    val relationships: List<KnowledgeRelationship>,

    val score: Float
)

data class KnowledgeAnswer(
    val answer: String,

    val entities: List<KnowledgeEntity> = emptyList(),

    val relationships: List<KnowledgeRelationship> = emptyList(),

    val evidence: List<String> = emptyList(),

    val confidence: KnowledgeConfidence = KnowledgeConfidence.MEDIUM
)

data class KnowledgeConflict(
    val key: String,

    val existingValue: String,

    val newValue: String,

    val existingSource: KnowledgeSource,

    val newSource: KnowledgeSource,

    val detectedAt: Long = System.currentTimeMillis()
)

data class KnowledgeContext(
    val relevantEntities: List<KnowledgeEntity>,

    val relevantRelationships: List<KnowledgeRelationship>,

    val relevantObservations: List<KnowledgeObservation>,

    val relationshipPaths: List<RelationshipPath> = emptyList(),

    val conflicts: List<KnowledgeConflict> = emptyList()
)
