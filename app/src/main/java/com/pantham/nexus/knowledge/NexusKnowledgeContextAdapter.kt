package com.pantham.nexus.knowledge

class NexusKnowledgeContextAdapter(
    private val engine: NexusPersonalKnowledgeEngine
) {

    suspend fun buildRelevantContext(
        userText: String
    ): KnowledgeContext {

        val results =
            engine.search(userText)

        if (results.isEmpty()) {
            return KnowledgeContext(
                relevantEntities =
                    emptyList(),
                relevantRelationships =
                    emptyList(),
                relevantObservations =
                    emptyList()
            )
        }

        val selected =
            results
                .take(8)
                .map {
                    it.entity
                }

        val relationships =
            selected
                .flatMap {
                    engineContextRelationships(
                        it.id
                    )
                }
                .distinctBy {
                    it.id
                }

        val observations =
            selected
                .flatMap {
                    engineContextObservations(
                        it.id
                    )
                }
                .distinctBy {
                    it.id
                }

        return KnowledgeContext(
            relevantEntities =
                selected,
            relevantRelationships =
                relationships,
            relevantObservations =
                observations
        )
    }

    private suspend fun engineContextRelationships(
        entityId: String
    ): List<KnowledgeRelationship> {
        return engine.repository.findRelationships(entityId)
    }

    private suspend fun engineContextObservations(
        entityId: String
    ): List<KnowledgeObservation> {
        return engine.repository.getObservations(entityId)
    }
}
