package com.pantham.nexus.files.chunker

import com.pantham.nexus.files.model.FileContentChunk

class NexusFileChunker(
    private val maxChars: Int = 1800,
    private val overlapChars: Int = 250
) {

    fun chunk(
        fileId: String,
        text: String,
        pageNumber: Int? = null
    ): List<FileContentChunk> {

        val clean = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()

        if (clean.isBlank()) {
            return emptyList()
        }

        val paragraphs = clean
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val chunks = mutableListOf<FileContentChunk>()

        var current = StringBuilder()
        var chunkIndex = 0

        fun flush() {

            if (current.isEmpty()) {
                return
            }

            val value = current
                .toString()
                .trim()

            if (value.isNotBlank()) {

                chunks += FileContentChunk(
                    chunkId = "$fileId:$chunkIndex",
                    fileId = fileId,
                    text = value,
                    pageNumber = pageNumber,
                    chunkIndex = chunkIndex,
                    tokenEstimate = value.length / 4
                )

                chunkIndex++
            }

            current = StringBuilder()
        }

        for (paragraph in paragraphs) {

            if (paragraph.length > maxChars) {

                flush()

                var start = 0

                while (start < paragraph.length) {

                    val end =
                        minOf(
                            start + maxChars,
                            paragraph.length
                        )

                    val piece =
                        paragraph.substring(
                            start,
                            end
                        )

                    chunks += FileContentChunk(
                        chunkId = "$fileId:$chunkIndex",
                        fileId = fileId,
                        text = piece,
                        pageNumber = pageNumber,
                        chunkIndex = chunkIndex,
                        startOffset = start,
                        endOffset = end,
                        tokenEstimate = piece.length / 4
                    )

                    chunkIndex++

                    if (end >= paragraph.length) {
                        break
                    }

                    start =
                        maxOf(
                            end - overlapChars,
                            start + 1
                        )
                }

                continue
            }

            if (
                current.isNotEmpty() &&
                current.length + paragraph.length + 2 > maxChars
            ) {
                flush()
            }

            if (current.isNotEmpty()) {
                current.append("\n\n")
            }

            current.append(paragraph)
        }

        flush()

        return chunks
    }
}
