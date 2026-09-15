package com.pantham.nexus.files.index

import com.pantham.nexus.files.model.SemanticFileIndexItem

interface NexusFileIndexRepository {

    suspend fun upsert(
        item: SemanticFileIndexItem
    )

    suspend fun get(
        fileId: String
    ): SemanticFileIndexItem?

    suspend fun getAll(
        limit: Int = 5000
    ): List<SemanticFileIndexItem>

    suspend fun remove(
        fileId: String
    )

    suspend fun clear()
}

class InMemoryNexusFileIndexRepository(
    private val maxItems: Int = 2000
) : NexusFileIndexRepository {

    private val items =
        LinkedHashMap<String, SemanticFileIndexItem>(
            16,
            0.75f,
            true
        )

    override suspend fun upsert(
        item: SemanticFileIndexItem
    ) {

        synchronized(items) {

            items[item.metadata.fileId] = item

            while (items.size > maxItems) {
                val first =
                    items.entries
                        .firstOrNull()
                        ?.key
                    ?: break

                items.remove(first)
            }
        }
    }

    override suspend fun get(
        fileId: String
    ): SemanticFileIndexItem? =
        synchronized(items) {
            items[fileId]
        }

    override suspend fun getAll(
        limit: Int
    ): List<SemanticFileIndexItem> =
        synchronized(items) {
            items.values
                .take(
                    limit.coerceIn(
                        1,
                        maxItems
                    )
                )
        }

    override suspend fun remove(
        fileId: String
    ) {
        synchronized(items) {
            items.remove(fileId)
        }
    }

    override suspend fun clear() {
        synchronized(items) {
            items.clear()
        }
    }
}
