package com.pantham.nexus.intelligence.context

import com.example.data.repository.EncryptedMemoryWalletRepository
import com.pantham.nexus.intelligence.model.MemoryCandidate
import kotlin.math.max

data class StoredMemory(
    val key: String,
    val value: String,
    val category: String? = null,
    val importance: Double = 0.5
)

interface MemorySource {

    suspend fun getAllMemories(): List<StoredMemory>
}

class NexusMemoryRelevanceEngine(
    private val source: MemorySource
) : RelevantMemoryResolver {

    override suspend fun findRelevantMemories(
        input: String,
        limit: Int
    ): List<MemoryCandidate> {

        val normalizedInput =
            normalize(input)

        return source
            .getAllMemories()
            .asSequence()
            .map { memory ->

                val keyScore =
                    tokenSimilarity(
                        normalizedInput,
                        normalize(memory.key)
                    )

                val valueScore =
                    tokenSimilarity(
                        normalizedInput,
                        normalize(memory.value)
                    )

                val categoryScore =
                    if (
                        !memory.category.isNullOrBlank() &&
                        normalizedInput.contains(
                            normalize(memory.category)
                        )
                    ) 1.0
                    else 0.0

                val relevance =
                    (
                        keyScore * 0.45 +
                        valueScore * 0.40 +
                        categoryScore * 0.15 +
                        memory.importance * 0.05
                    ).coerceIn(0.0, 1.0)

                MemoryCandidate(
                    key = memory.key,
                    value = memory.value,
                    relevance = relevance,
                    category = memory.category
                )
            }
            .filter {
                it.relevance >= 0.18
            }
            .sortedByDescending {
                it.relevance
            }
            .take(limit)
            .toList()
    }

    private fun normalize(
        value: String
    ): String {

        return value
            .lowercase()
            .replace(
                Regex("[^\\p{L}\\p{N}\\s]"),
                " "
            )
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }

    private fun tokenSimilarity(
        a: String,
        b: String
    ): Double {

        if (a.isBlank() || b.isBlank()) {
            return 0.0
        }

        val aTokens =
            a.split(" ")
                .filter { it.length > 1 }
                .toSet()

        val bTokens =
            b.split(" ")
                .filter { it.length > 1 }
                .toSet()

        if (aTokens.isEmpty() || bTokens.isEmpty()) {
            return 0.0
        }

        val intersection =
            aTokens.intersect(bTokens).size.toDouble()

        val union =
            max(
                1,
                aTokens.union(bTokens).size
            ).toDouble()

        return intersection / union
    }
}

/**
 * Adapter that connects the Room/SQLCipher-based [EncryptedMemoryWalletRepository]
 * into the [MemorySource] contract.
 */
class EncryptedWalletMemorySource(
    private val walletRepository: EncryptedMemoryWalletRepository
) : MemorySource {

    override suspend fun getAllMemories(): List<StoredMemory> {
        val walletItems = walletRepository.getAllWalletItemsList()
        val preferences = walletRepository.getAllPreferencesList()

        val results = ArrayList<StoredMemory>(walletItems.size + preferences.size)
        for (item in walletItems) {
            results.add(
                StoredMemory(
                    key = item.title,
                    value = item.content,
                    category = item.tag,
                    importance = 0.75
                )
            )
        }
        for (pref in preferences) {
            results.add(
                StoredMemory(
                    key = pref.key,
                    value = pref.value,
                    category = pref.category,
                    importance = 0.5
                )
            )
        }
        return results
    }
}
