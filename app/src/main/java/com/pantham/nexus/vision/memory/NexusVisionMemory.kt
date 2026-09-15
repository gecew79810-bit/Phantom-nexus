package com.pantham.nexus.vision.memory

import com.pantham.nexus.vision.model.VisualContext

interface VisionMemoryRepository {

    suspend fun save(
        context: VisualContext
    )

    suspend fun getRecent(
        limit: Int
    ): List<VisualContext>

    suspend fun clear()
}

class InMemoryVisionRepository :
    VisionMemoryRepository {

    private val items =
        ArrayDeque<VisualContext>()

    override suspend fun save(
        context: VisualContext
    ) {

        items.addFirst(
            context
        )

        while (
            items.size > 10
        ) {
            items.removeLast()
        }
    }

    override suspend fun getRecent(
        limit: Int
    ): List<VisualContext> {

        return items
            .take(limit)
    }

    override suspend fun clear() {
        items.clear()
    }
}
