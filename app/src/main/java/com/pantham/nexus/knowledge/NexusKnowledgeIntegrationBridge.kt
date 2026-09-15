package com.pantham.nexus.knowledge

data class KnowledgeAugmentedContext(
    val originalText: String,

    val knowledgeContext: KnowledgeContext,

    val contextSummary: String
)

class NexusKnowledgeIntegrationBridge(
    private val controller: NexusKnowledgeController
) {

    suspend fun augment(
        userText: String
    ): KnowledgeAugmentedContext {

        val context =
            controller.processConversation(
                userText
            )

        val summary =
            buildSummary(context)

        return KnowledgeAugmentedContext(
            originalText = userText,
            knowledgeContext = context,
            contextSummary = summary
        )
    }

    private fun buildSummary(
        context: KnowledgeContext
    ): String {

        if (
            context.relevantEntities.isEmpty()
        ) {
            return ""
        }

        return buildString {

            append("Relevant personal knowledge:\n")

            context.relevantEntities
                .take(8)
                .forEach {

                    append(
                        "- ${it.canonicalName} " +
                            "(${it.type})"
                    )

                    if (
                        it.attributes.isNotEmpty()
                    ) {

                        append(
                            " attributes=" +
                                it.attributes
                        )
                    }

                    append("\n")
                }

            context.relevantRelationships
                .take(12)
                .forEach {

                    append(
                        "- relationship: " +
                            it.relationshipType +
                            "\n"
                    )
                }

            context.relevantObservations
                .take(12)
                .forEach {

                    append(
                        "- fact: " +
                            it.predicate +
                            "=" +
                            it.value +
                            "\n"
                    )
                }
        }
    }
}
