package com.pantham.nexus.files.search

import com.pantham.nexus.files.model.FileMatchReason
import com.pantham.nexus.files.model.FileSearchResult
import com.pantham.nexus.files.model.SemanticFileIndexItem
import kotlin.math.max
import kotlin.math.min

class NexusFileRanker {

    fun rank(
        item: SemanticFileIndexItem,
        queryTerms: List<String>,
        queryEntityTerms: List<String> = emptyList(),
        semanticScore: Float = 0f,
        preferredMime: String? = null,
        intentRecent: Boolean = false
    ): FileSearchResult {

        val name =
            item.metadata.name.lowercase()

        val searchable =
            buildString {

                append(name)
                append(" ")

                item.summary?.let {
                    append(it)
                    append(" ")
                }

                item.topics.forEach {
                    append(it)
                    append(" ")
                }

                item.chunks.forEach {
                    append(it.text)
                    append(" ")
                }

                item.extractedEntities.forEach {
                    append(it.text)
                    append(" ")
                }
            }.lowercase()

        val filenameHits =
            queryTerms.count {
                name.contains(it)
            }

        val contentHits =
            queryTerms.count {
                searchable.contains(it)
            }

        val fuzzyHits =
            queryTerms.count { queryTerm ->
                searchable
                    .split(Regex("\\W+"))
                    .any {
                        similarity(
                            queryTerm,
                            it
                        ) >= 0.78f
                    }
            }

        val entityHits =
            queryEntityTerms.count { entity ->
                searchable.contains(
                    entity.lowercase()
                )
            }

        val mimeScore =
            if (
                preferredMime != null &&
                item.metadata.mimeType == preferredMime
            ) 1f else 0f

        val recencyScore =
            recencyScore(
                item.metadata.lastModified
            )

        val semantic =
            semanticScore
                .coerceIn(0f, 1f)

        val filenameScore =
            normalize(
                filenameHits,
                queryTerms.size
            )

        val contentScore =
            normalize(
                contentHits,
                queryTerms.size * 3
            )

        val fuzzyScore =
            normalize(
                fuzzyHits,
                queryTerms.size
            )

        val entityScore =
            normalize(
                entityHits,
                queryEntityTerms.size
            )

        val lexicalScore =
            (
                filenameScore * 0.40f +
                    contentScore * 0.40f +
                    fuzzyScore * 0.20f
                )
                .coerceIn(0f, 1f)

        val base =
            if (semanticScore > 0f) {
                lexicalScore * 0.65f +
                    semantic * 0.35f
            } else {
                lexicalScore
            }

        val finalScore =
            (
                base * 0.78f +
                    entityScore * 0.08f +
                    recencyScore * 0.08f +
                    mimeScore * 0.04f
                )
                .coerceIn(0f, 1f)

        val reasons = mutableListOf<FileMatchReason>()

        if (filenameScore > 0f) {
            reasons += FileMatchReason(
                "filename",
                filenameScore
            )
        }

        if (contentScore > 0f) {
            reasons += FileMatchReason(
                "content",
                contentScore
            )
        }

        if (fuzzyScore > 0f) {
            reasons += FileMatchReason(
                "fuzzy",
                fuzzyScore
            )
        }

        if (semantic > 0f) {
            reasons += FileMatchReason(
                "semantic",
                semantic
            )
        }

        if (entityScore > 0f) {
            reasons += FileMatchReason(
                "entity",
                entityScore
            )
        }

        if (mimeScore > 0f) {
            reasons += FileMatchReason(
                "file-type",
                mimeScore
            )
        }

        if (intentRecent) {
            reasons += FileMatchReason(
                "recency",
                recencyScore
            )
        }

        val bestChunk =
            item.chunks
                .maxByOrNull { chunk ->
                    queryTerms.count {
                        chunk.text
                            .lowercase()
                            .contains(it)
                    }
                }

        val snippet =
            bestChunk?.text
                ?.replace(
                    Regex("\\s+"),
                    " "
                )
                ?.take(280)

        return FileSearchResult(
            fileId = item.metadata.fileId,
            name = item.metadata.name,
            uri = item.metadata.uri,
            matchScore = finalScore,
            snippet = snippet,
            matchedChunk = bestChunk,
            recencyScore = recencyScore,
            reasoning = reasons
                .sortedByDescending {
                    it.score
                }
                .take(4)
                .joinToString(", ") {
                    it.signal
                },
            reasons = reasons
        )
    }

    private fun normalize(
        value: Int,
        denominator: Int
    ): Float {

        if (denominator <= 0) {
            return 0f
        }

        return min(
            1f,
            value.toFloat() / denominator
        )
    }

    private fun recencyScore(
        timestamp: Long?
    ): Float {

        if (timestamp == null || timestamp <= 0L) {
            return 0f
        }

        val age =
            (System.currentTimeMillis() -
                timestamp)
                .coerceAtLeast(0L)

        val days =
            age / 86_400_000.0

        return when {
            days <= 1 -> 1f
            days <= 7 -> 0.85f
            days <= 30 -> 0.65f
            days <= 90 -> 0.45f
            days <= 365 -> 0.25f
            else -> 0.10f
        }
    }

    private fun similarity(
        a: String,
        b: String
    ): Float {

        if (a.isBlank() || b.isBlank()) {
            return 0f
        }

        val aa = a.lowercase()
        val bb = b.lowercase()

        if (aa == bb) {
            return 1f
        }

        if (aa.length < 3 || bb.length < 3) {
            return 0f
        }

        val distance =
            levenshtein(
                aa,
                bb
            )

        return 1f -
            distance.toFloat() /
            max(
                aa.length,
                bb.length
            )
    }

    private fun levenshtein(
        a: String,
        b: String
    ): Int {

        val prev =
            IntArray(
                b.length + 1
            ) {
                it
            }

        val curr =
            IntArray(
                b.length + 1
            )

        for (i in a.indices) {

            curr[0] = i + 1

            for (j in b.indices) {

                val cost =
                    if (a[i] == b[j]) 0 else 1

                curr[j + 1] =
                    min(
                        min(
                            curr[j] + 1,
                            prev[j + 1] + 1
                        ),
                        prev[j] + cost
                    )
            }

            for (j in prev.indices) {
                prev[j] = curr[j]
            }
        }

        return prev[b.length]
    }
}
