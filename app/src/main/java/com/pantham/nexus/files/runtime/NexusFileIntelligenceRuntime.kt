package com.pantham.nexus.files.runtime

import android.content.Context
import android.net.Uri
import com.example.action.goal.ArtifactRegistry
import com.example.action.goal.TaskArtifact
import com.pantham.nexus.files.adapter.ArtifactRegistryFileAdapter
import com.pantham.nexus.files.adapter.NexusFileContextAdapter
import com.pantham.nexus.files.chunker.NexusFileChunker
import com.pantham.nexus.files.extractor.*
import com.pantham.nexus.files.index.*
import com.pantham.nexus.files.model.*
import com.pantham.nexus.files.search.*
import com.pantham.nexus.files.security.NexusFilePrivacyGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class NexusFileIntelligenceRuntime private constructor(
    private val appContext: Context,

    private val repository:
        NexusFileIndexRepository,

    private val embeddingProvider:
        FileEmbeddingProvider,

    private val visionBridge:
        ExistingVisionFileBridge?
) {

    private val chunker =
        NexusFileChunker()

    private val privacyGate =
        NexusFilePrivacyGate()

    private val queryRewriter =
        NexusFileQueryRewriter()

    private val searchEngine =
        NexusFileHybridSearchEngine(
            embeddingProvider =
                embeddingProvider
        )

    private val contextAdapter =
        NexusFileContextAdapter()

    private val artifactAdapter =
        ArtifactRegistryFileAdapter(
            ArtifactRegistry
                .getInstance(
                    appContext
                )
        )

    private val extractionAdapters =
        listOf<ContentExtractionAdapter>(

            TextExtractionAdapter(),

            PdfExtractionAdapter(),

            DocxExtractionAdapter(),

            ImageExtractionAdapter(
                visionBridge
            )
        )

    private var activeFile:
        FileMetadata? = null


    suspend fun indexUri(
        uri: Uri,
        fileId: String,
        name: String,
        mimeType: String? = null,
        sizeBytes: Long? = null,
        lastModified: Long? = null
    ): FileIndexResult =
        withContext(Dispatchers.IO) {

            try {

                if (
                    !privacyGate.canAccess(
                        appContext,
                        uri
                    )
                ) {

                    return@withContext FileIndexResult(
                        success = false,
                        error = FileIndexError(
                            fileId =
                                fileId,
                            message =
                                "File URI is not accessible by privacy gate.",
                            recoverable = true
                        )
                    )
                }

                val extension =
                    name
                        .substringAfterLast(
                            '.',
                            ""
                        )
                        .lowercase()
                        .ifBlank {
                            null
                        }

                val metadata =
                    FileMetadata(
                        fileId =
                            fileId,
                        uri =
                            uri.toString(),
                        name =
                            name,
                        extension =
                            extension,
                        mimeType =
                            mimeType,
                        sizeBytes =
                            sizeBytes,
                        lastModified =
                            lastModified
                    )

                val existing =
                    repository.get(
                        fileId
                    )

                val hash =
                    calculateHash(
                        uri
                    )

                if (
                    existing != null &&
                    existing.metadata.contentHash == hash &&
                    existing.metadata.lastModified ==
                    lastModified
                ) {

                    activeFile =
                        metadata.copy(
                            contentHash =
                                hash
                        )

                    return@withContext FileIndexResult(
                        success = true,
                        item =
                            existing
                    )
                }

                val adapter =
                    extractionAdapters
                        .firstOrNull {
                            it.supports(
                                mimeType,
                                extension
                            )
                        }
                        ?: return@withContext FileIndexResult(
                            success = false,
                            error = FileIndexError(
                                fileId =
                                    fileId,
                                message =
                                    "Unsupported file format.",
                                recoverable = true
                            )
                        )

                val extraction =
                    adapter.extract(
                        context =
                            appContext,
                        uri =
                            uri,
                        fileId =
                            fileId
                    )

                if (!extraction.success) {

                    return@withContext FileIndexResult(
                        success = false,
                        error = FileIndexError(
                            fileId =
                                fileId,
                            message =
                                extraction.error
                                    ?: "Extraction failed.",
                            recoverable = true
                        )
                    )
                }

                val safeText =
                    privacyGate.sanitizeText(
                        extraction.content
                            ?.text
                            .orEmpty()
                    )

                val chunks =
                    chunker.chunk(
                        fileId =
                            fileId,
                        text =
                            safeText
                    )
                        .map(
                            privacyGate::sanitizeChunk
                        )

                val finalMetadata =
                    metadata.copy(
                        contentHash =
                            hash,
                        pageCount =
                            extraction.content
                                ?.pageCount
                    )

                val item =
                    SemanticFileIndexItem(
                        metadata =
                            finalMetadata,
                        chunks =
                            chunks,
                        summary =
                            createBoundedSummary(
                                safeText
                            ),
                        topics =
                            extractTopics(
                                safeText
                            )
                    )

                repository.upsert(
                    item
                )

                activeFile =
                    finalMetadata

                FileIndexResult(
                    success = true,
                    item = item
                )

            } catch (t: Throwable) {

                FileIndexResult(
                    success = false,
                    error = FileIndexError(
                        fileId =
                            fileId,
                        message =
                            t.message
                                ?: "Unknown indexing error.",
                        recoverable = true
                    )
                )
            }
        }


    suspend fun indexArtifact(
        artifact: TaskArtifact
    ): FileIndexResult {

        val metadata =
            artifactAdapter.toMetadata(
                artifact
            )

        return indexUri(
            uri =
                Uri.parse(
                    metadata.uri
                ),
            fileId =
                metadata.fileId,
            name =
                metadata.name,
            mimeType =
                metadata.mimeType,
            sizeBytes =
                metadata.sizeBytes,
            lastModified =
                metadata.lastModified
        )
    }


    suspend fun search(
        query: FileSearchQuery
    ): List<FileSearchResult> {

        val items =
            repository.getAll()

        val results =
            searchEngine.search(
                query =
                    query,
                items =
                    items
            )

        val matchedFiles =
            results.mapNotNull { result ->
                repository.get(
                    result.fileId
                )?.metadata
            }

        contextAdapter.update(
            recentFiles =
                getRecentFiles(),

            queriedFiles =
                matchedFiles,

            topMatches =
                results,

            activeFile =
                activeFile
        )

        return results
    }


    suspend fun refresh() {

        val artifacts =
            try {
                artifactAdapter
                    .getArtifacts()
            } catch (_: Throwable) {
                emptyList()
            }

        for (artifact in artifacts) {

            try {
                indexArtifact(
                    artifact
                )
            } catch (_: Throwable) {
                // Continue with next artifact.
            }
        }
    }


    suspend fun getContext():
        FileIntelligenceContext {

        contextAdapter.update(
            recentFiles =
                getRecentFiles(),

            queriedFiles =
                contextAdapter.current()
                    .queriedFiles,

            topMatches =
                contextAdapter.current()
                    .topMatches,

            activeFile =
                activeFile
        )

        return contextAdapter.current()
    }


    fun setActiveFile(
        fileId: String
    ) {
        activeFile = null
    }


    suspend fun clearIndex() {
        repository.clear()
    }


    private suspend fun getRecentFiles():
        List<FileMetadata> {

        return repository
            .getAll()
            .map {
                it.metadata
            }
            .sortedByDescending {
                it.lastModified ?: 0L
            }
            .take(20)
    }


    private fun createBoundedSummary(
        text: String
    ): String? {

        val clean =
            text.replace(
                Regex("\\s+"),
                " "
            ).trim()

        if (clean.isBlank()) {
            return null
        }

        return clean.take(700)
    }


    private fun extractTopics(
        text: String
    ): List<String> {

        if (text.isBlank()) {
            return emptyList()
        }

        return text
            .lowercase()
            .split(
                Regex(
                    "[^\\p{L}\\p{N}]+"
                )
            )
            .filter {
                it.length >= 5
            }
            .groupingBy {
                it
            }
            .eachCount()
            .entries
            .sortedByDescending {
                it.value
            }
            .take(10)
            .map {
                it.key
            }
    }


    private fun calculateHash(
        uri: Uri
    ): String? {

        return try {

            val digest =
                MessageDigest.getInstance(
                    "SHA-256"
                )

            appContext
                .contentResolver
                .openInputStream(
                    uri
                )
                ?.use { input ->

                    val buffer =
                        ByteArray(
                            16 * 1024
                        )

                    var read: Int

                    var total =
                        0L

                    val max =
                        50L * 1024L * 1024L

                    while (
                        input.read(
                            buffer
                        ).also {
                            read = it
                        } > 0
                    ) {

                        total += read

                        if (total > max) {
                            break
                        }

                        digest.update(
                            buffer,
                            0,
                            read
                        )
                    }

                    digest.digest()
                        .joinToString("") {
                            "%02x"
                                .format(it)
                        }
                }

        } catch (_: Throwable) {
            null
        }
    }


    companion object {

        @Volatile
        private var INSTANCE:
            NexusFileIntelligenceRuntime? =
            null


        fun initialize(
            context: Context,
            embeddingProvider:
                FileEmbeddingProvider =
                NullFileEmbeddingProvider(),

            visionBridge:
                ExistingVisionFileBridge? =
                null
        ):
            NexusFileIntelligenceRuntime {

            return INSTANCE
                ?: synchronized(this) {

                    INSTANCE
                        ?: NexusFileIntelligenceRuntime(
                            appContext =
                                context.applicationContext,

                            repository =
                                InMemoryNexusFileIndexRepository(),

                            embeddingProvider =
                                embeddingProvider,

                            visionBridge =
                                visionBridge
                        )
                            .also {
                                INSTANCE = it
                            }
                }
        }


        fun getInstance():
            NexusFileIntelligenceRuntime {

            return INSTANCE
                ?: error(
                    "NexusFileIntelligenceRuntime is not initialized."
                )
        }
    }
}
