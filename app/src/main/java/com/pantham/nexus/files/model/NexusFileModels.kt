package com.pantham.nexus.files.model

import java.util.Locale

enum class FileSearchIntent {
    FIND,
    OPEN,
    COMPARE,
    SUMMARIZE,
    LOCATE_INFORMATION,
    RECENT,
    OLDEST,
    BY_TYPE,
    BY_ENTITY,
    UNKNOWN
}

data class FileMetadata(
    val fileId: String,
    val uri: String,
    val name: String,
    val extension: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val lastModified: Long? = null,
    val pageCount: Int? = null,
    val contentHash: String? = null
)

data class ExtractedEntity(
    val text: String,
    val type: String = "UNKNOWN",
    val confidence: Float = 0.5f
)

data class FileContentChunk(
    val chunkId: String,
    val fileId: String,
    val text: String,
    val pageNumber: Int? = null,
    val chunkIndex: Int,
    val startOffset: Int? = null,
    val endOffset: Int? = null,
    val tokenEstimate: Int = text.length / 4
)

data class SemanticFileIndexItem(
    val metadata: FileMetadata,
    val chunks: List<FileContentChunk> = emptyList(),
    val summary: String? = null,
    val extractedEntities: List<ExtractedEntity> = emptyList(),
    val topics: List<String> = emptyList(),
    val indexedAt: Long = System.currentTimeMillis()
)

data class FileSearchQuery(
    val queryText: String,
    val filters: Map<String, String> = emptyMap(),
    val preferredMime: String? = null,
    val temporalFrom: Long? = null,
    val temporalTo: Long? = null,
    val requiredEntities: List<String> = emptyList(),
    val intent: FileSearchIntent = FileSearchIntent.UNKNOWN,
    val maxResults: Int = 10
)

data class RewrittenFileQuery(
    val original: String,
    val terms: List<String>,
    val normalizedTerms: List<String>,
    val probableExtension: String? = null,
    val probableMimeType: String? = null,
    val temporalFrom: Long? = null,
    val temporalTo: Long? = null,
    val entities: List<String> = emptyList(),
    val intent: FileSearchIntent = FileSearchIntent.UNKNOWN
)

data class FileMatchReason(
    val signal: String,
    val score: Float
)

data class FileSearchResult(
    val fileId: String,
    val name: String,
    val uri: String,
    val matchScore: Float,
    val snippet: String? = null,
    val matchedChunk: FileContentChunk? = null,
    val recencyScore: Float = 0f,
    val reasoning: String = "",
    val reasons: List<FileMatchReason> = emptyList()
)

data class FileIntelligenceContext(
    val generatedAt: Long = System.currentTimeMillis(),
    val recentFiles: List<FileMetadata> = emptyList(),
    val queriedFiles: List<FileMetadata> = emptyList(),
    val topMatches: List<FileSearchResult> = emptyList(),
    val activeFile: FileMetadata? = null
)

data class FileIndexError(
    val fileId: String?,
    val message: String,
    val recoverable: Boolean = true
)

data class FileIndexResult(
    val success: Boolean,
    val item: SemanticFileIndexItem? = null,
    val error: FileIndexError? = null
)

data class ExtractedFileContent(
    val fileId: String,
    val text: String,
    val pageCount: Int? = null,
    val metadata: Map<String, String> = emptyMap(),
    val warnings: List<String> = emptyList()
)

data class FileContentExtractionResult(
    val success: Boolean,
    val content: ExtractedFileContent? = null,
    val error: String? = null
)

fun String.normalizedFileText(): String =
    lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
        .trim()
