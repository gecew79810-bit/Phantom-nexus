package com.pantham.nexus.files.search

import com.pantham.nexus.files.model.FileSearchQuery
import com.pantham.nexus.files.model.FileSearchResult
import com.pantham.nexus.files.model.SemanticFileIndexItem
import com.pantham.nexus.files.model.RewrittenFileQuery

interface FileEmbeddingProvider {

    suspend fun embed(
        text: String
    ): FloatArray?
}

class NullFileEmbeddingProvider :
    FileEmbeddingProvider {

    override suspend fun embed(
        text: String
    ): FloatArray? = null
}

class NexusFileHybridSearchEngine(
    private val ranker: NexusFileRanker =
        NexusFileRanker(),

    private val queryRewriter:
        NexusFileQueryRewriter =
        NexusFileQueryRewriter(),

    private val embeddingProvider:
        FileEmbeddingProvider =
        NullFileEmbeddingProvider()
) {

    suspend fun search(
        query: FileSearchQuery,
        items: Collection<SemanticFileIndexItem>
    ): List<FileSearchResult> {

        if (query.queryText.isBlank()) {
            return emptyList()
        }

        val rewritten =
            queryRewriter.rewrite(query)

        val candidates =
            candidates(
                rewritten,
                items
            )

        if (candidates.isEmpty()) {
            return emptyList()
        }

        val queryEmbedding =
            try {
                embeddingProvider.embed(
                    rewritten.normalizedTerms
                        .joinToString(" ")
                )
            } catch (_: Throwable) {
                null
            }

        return candidates
            .map { item ->

                val semanticScore =
                    if (queryEmbedding != null) {
                        semanticSimilarity(
                            queryEmbedding,
                            item
                        )
                    } else {
                        0f
                    }

                ranker.rank(
                    item = item,
                    queryTerms =
                        rewritten.normalizedTerms,
                    queryEntityTerms =
                        rewritten.entities,
                    semanticScore =
                        semanticScore,
                    preferredMime =
                        query.preferredMime
                            ?: rewritten.probableMimeType,
                    intentRecent =
                        rewritten.intent.name == "RECENT"
                )
            }
            .sortedByDescending {
                it.matchScore
            }
            .take(
                query.maxResults
                    .coerceIn(1, 50)
            )
    }

    private fun candidates(
        query: RewrittenFileQuery,
        items: Collection<SemanticFileIndexItem>
    ): List<SemanticFileIndexItem> {

        return items
            .asSequence()
            .filter { item ->

                if (
                    query.probableExtension != null
                ) {

                    val ext =
                        item.metadata.extension
                            ?.trimStart('.')
                            ?.lowercase()

                    if (
                        ext != query.probableExtension
                    ) {
                        return@filter false
                    }
                }

                if (query.temporalFrom != null) {

                    val modified =
                        item.metadata.lastModified
                            ?: return@filter false

                    if (
                        modified <
                        query.temporalFrom
                    ) {
                        return@filter false
                    }
                }

                if (query.temporalTo != null) {

                    val modified =
                        item.metadata.lastModified
                            ?: return@filter false

                    if (
                        modified >
                        query.temporalTo
                    ) {
                        return@filter false
                    }
                }

                true
            }
            .filter { item ->

                val haystack =
                    buildString {

                        append(
                            item.metadata.name
                        )
                        append(" ")

                        item.summary?.let {
                            append(it)
                        }

                        item.chunks
                            .take(20)
                            .forEach {
                                append(" ")
                                append(it.text.take(1000))
                            }
                    }.lowercase()

                query.normalizedTerms.any {
                    haystack.contains(it)
                } || query.entities.any {
                    haystack.contains(
                        it.lowercase()
                    )
                }
            }
            .take(250)
            .toList()
    }

    private fun semanticSimilarity(
        queryEmbedding: FloatArray,
        item: SemanticFileIndexItem
    ): Float {

        // V1 intentionally does not require stored embeddings.
        // If embedding vectors are attached by a future index
        // implementation, this method is the integration point.

        return 0f
    }
}
