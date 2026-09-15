package com.pantham.nexus.files

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.action.goal.ArtifactRegistry
import com.example.action.goal.ArtifactType
import com.example.action.goal.TaskArtifact
import com.pantham.nexus.files.adapter.ArtifactRegistryFileAdapter
import com.pantham.nexus.files.adapter.NexusFileContextAdapter
import com.pantham.nexus.files.chunker.NexusFileChunker
import com.pantham.nexus.files.extractor.DocxExtractionAdapter
import com.pantham.nexus.files.extractor.ExistingVisionFileBridge
import com.pantham.nexus.files.extractor.ImageExtractionAdapter
import com.pantham.nexus.files.extractor.PdfExtractionAdapter
import com.pantham.nexus.files.extractor.TextExtractionAdapter
import com.pantham.nexus.files.graph.ExistingKnowledgeFileBridge
import com.pantham.nexus.files.graph.NexusFileRelationshipEngine
import com.pantham.nexus.files.index.InMemoryNexusFileIndexRepository
import com.pantham.nexus.files.model.*
import com.pantham.nexus.files.search.*
import com.pantham.nexus.files.security.NexusFilePrivacyGate
import com.pantham.nexus.intelligence.model.InputChannel
import com.pantham.nexus.intelligence.model.NexusContext
import com.pantham.nexus.knowledge.KnowledgeEntity
import com.pantham.nexus.knowledge.KnowledgeEntityType
import com.pantham.nexus.knowledge.KnowledgeSource
import com.pantham.nexus.prediction.PredictiveContext
import com.pantham.nexus.situational.model.SituationalContext
import com.pantham.nexus.vision.model.VisionSource
import com.pantham.nexus.vision.model.VisualContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NexusFileIntelligenceTests {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    // 1. Query Rewriter Detects PDF
    @Test
    fun query_rewriter_detects_pdf() {
        val result = NexusFileQueryRewriter().rewrite(
            FileSearchQuery(queryText = "inverter warranty PDF")
        )
        assertEquals("pdf", result.probableExtension)
        assertTrue(result.terms.any { it.contains("inverter") })
    }

    // 2. Query Rewriter Detects Other Formats and Intent
    @Test
    fun query_rewriter_detects_docx_and_intent() {
        val result = NexusFileQueryRewriter().rewrite(
            FileSearchQuery(queryText = "open my salary report docx")
        )
        assertEquals("docx", result.probableExtension)
        assertEquals(FileSearchIntent.OPEN, result.intent)
        assertTrue(result.terms.contains("salary"))
    }

    // 3. Unknown Query Does Not Crash
    @Test
    fun unknown_query_does_not_crash() {
        val result = NexusFileQueryRewriter().rewrite(
            FileSearchQuery(queryText = "xyz random")
        )
        assertEquals(FileSearchIntent.FIND, result.intent)
    }

    // 4. Chunker Preserves Order
    @Test
    fun chunker_preserves_order() {
        val chunks = NexusFileChunker(
            maxChars = 50,
            overlapChars = 5
        ).chunk(
            fileId = "f1",
            text = """
                First paragraph about warranty.

                Second paragraph about inverter.

                Third paragraph about service.
            """.trimIndent()
        )

        assertTrue(chunks.isNotEmpty())
        assertEquals(0, chunks.first().chunkIndex)
        assertEquals(1, chunks[1].chunkIndex)
    }

    // 5. Chunker Handles Empty or Large Text
    @Test
    fun chunker_handles_empty_and_long_text() {
        val chunker = NexusFileChunker(maxChars = 100, overlapChars = 20)
        val emptyChunks = chunker.chunk("empty", "")
        assertTrue(emptyChunks.isEmpty())

        val longText = "A".repeat(350)
        val longChunks = chunker.chunk("long", longText)
        assertTrue(longChunks.size >= 3)
    }

    // 6. Ranking Favors Filename Match
    @Test
    fun ranking_favors_filename_match() {
        val item = SemanticFileIndexItem(
            metadata = FileMetadata(
                fileId = "1",
                uri = "content://file",
                name = "inverter-warranty.pdf",
                extension = "pdf"
            ),
            chunks = listOf(
                FileContentChunk(
                    chunkId = "1:0",
                    fileId = "1",
                    text = "Warranty details.",
                    chunkIndex = 0
                )
            )
        )

        val result = NexusFileRanker().rank(
            item = item,
            queryTerms = listOf("inverter", "warranty")
        )

        assertTrue(result.matchScore > 0f)
        assertTrue(result.reasoning.contains("filename"))
    }

    // 7. Ranking Recency
    @Test
    fun ranking_favors_recency() {
        val now = System.currentTimeMillis()
        val oldItem = SemanticFileIndexItem(
            metadata = FileMetadata(
                fileId = "old",
                uri = "content://old",
                name = "report.pdf",
                lastModified = now - 1000L * 60 * 60 * 24 * 300 // ~300 days
            )
        )
        val newItem = SemanticFileIndexItem(
            metadata = FileMetadata(
                fileId = "new",
                uri = "content://new",
                name = "report.pdf",
                lastModified = now - 1000L * 60 * 60 * 2 // 2 hours
            )
        )

        val ranker = NexusFileRanker()
        val oldRes = ranker.rank(oldItem, listOf("report"), intentRecent = true)
        val newRes = ranker.rank(newItem, listOf("report"), intentRecent = true)

        assertTrue(newRes.recencyScore > oldRes.recencyScore)
    }

    // 8. Privacy Redacts Passwords and Tokens
    @Test
    fun privacy_redacts_password() {
        val gate = NexusFilePrivacyGate()
        val result = gate.sanitizeText("password: hello123 and token=abc123456789012345678901234567890")
        assertTrue(result.contains("[REDACTED]"))
        assertFalse(result.contains("hello123"))
    }

    // 9. Privacy Blocks Dangerous Schemes
    @Test
    fun privacy_blocks_dangerous_schemes() {
        val gate = NexusFilePrivacyGate()

        assertFalse(gate.canAccess(context, Uri.parse("file:///etc/passwd")))
        assertFalse(gate.canAccess(context, Uri.parse("ftp://malicious.host/file.txt")))
        assertFalse(gate.canAccess(context, Uri.parse("smb://nas/share/doc.pdf")))
    }

    // 10. Privacy Sanitize Chunk
    @Test
    fun privacy_sanitizes_chunk() {
        val gate = NexusFilePrivacyGate()
        val chunk = FileContentChunk(
            chunkId = "c1",
            fileId = "f1",
            text = "api_key: secret_api_key_value_here",
            chunkIndex = 0
        )
        val sanitized = gate.sanitizeChunk(chunk)
        assertTrue(sanitized.text.contains("[REDACTED]"))
        assertFalse(sanitized.text.contains("secret_api_key_value_here"))
    }

    // 11. InMemory Repository CRUD
    @Test
    fun in_memory_repository_crud() = runBlocking {
        val repo = InMemoryNexusFileIndexRepository(maxItems = 3)
        val item1 = SemanticFileIndexItem(metadata = FileMetadata("1", "content://1", "one.txt"))
        val item2 = SemanticFileIndexItem(metadata = FileMetadata("2", "content://2", "two.txt"))
        val item3 = SemanticFileIndexItem(metadata = FileMetadata("3", "content://3", "three.txt"))
        val item4 = SemanticFileIndexItem(metadata = FileMetadata("4", "content://4", "four.txt"))

        repo.upsert(item1)
        repo.upsert(item2)
        repo.upsert(item3)
        assertEquals(3, repo.getAll().size)

        repo.upsert(item4) // Evicts oldest (item1)
        assertEquals(3, repo.getAll().size)
        assertNull(repo.get("1"))
        assertNotNull(repo.get("4"))

        repo.remove("2")
        assertNull(repo.get("2"))

        repo.clear()
        assertTrue(repo.getAll().isEmpty())
    }

    // 12. Artifact Adapter Conversion
    @Test
    fun artifact_adapter_to_metadata() {
        val registry = ArtifactRegistry.getInstance(context)
        val adapter = ArtifactRegistryFileAdapter(registry)
        val artifact = TaskArtifact(
            artifactId = "art_123",
            taskId = "task_99",
            name = "salary_slip.pdf",
            description = "Monthly Salary Slip",
            type = ArtifactType.PDF,
            uri = "content://media/external/file/456",
            createdAt = 123456789L
        )

        val meta = adapter.toMetadata(artifact)
        assertEquals("art_123", meta.fileId)
        assertEquals("salary_slip.pdf", meta.name)
        assertEquals("pdf", meta.extension)
        assertEquals("application/pdf", meta.mimeType)
        assertEquals(123456789L, meta.lastModified)
    }

    // 13. Text Extraction Adapter Supports
    @Test
    fun text_extraction_adapter_supports() {
        val adapter = TextExtractionAdapter()
        assertTrue(adapter.supports("text/plain", "txt"))
        assertTrue(adapter.supports("text/csv", "csv"))
        assertTrue(adapter.supports("application/json", "json"))
        assertTrue(adapter.supports(null, "md"))
        assertFalse(adapter.supports("application/pdf", "pdf"))
    }

    // 14. PDF Extraction Adapter Supports
    @Test
    fun pdf_extraction_adapter_supports() {
        val adapter = PdfExtractionAdapter()
        assertTrue(adapter.supports("application/pdf", "pdf"))
        assertFalse(adapter.supports("text/plain", "txt"))
    }

    // 15. Docx Extraction Adapter Supports
    @Test
    fun docx_extraction_adapter_supports() {
        val adapter = DocxExtractionAdapter()
        assertTrue(adapter.supports("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"))
        assertFalse(adapter.supports("application/pdf", "pdf"))
    }

    // 16. Image Extraction Adapter with Vision Bridge
    @Test
    fun image_extraction_with_vision_bridge() = runBlocking {
        val fakeVisionBridge = object : ExistingVisionFileBridge {
            override suspend fun analyzeImage(context: Context, uri: Uri): VisualContext {
                return VisualContext(
                    frameId = "frame_ocr",
                    source = VisionSource.IMAGE_FILE,
                    description = "Electricity bill receipt",
                    visibleText = "Amount Due: Rs 2450 paid successfully",
                    detectedObjects = emptyList(),
                    detectedLabels = emptyList(),
                    barcodes = emptyList(),
                    timestamp = System.currentTimeMillis()
                )
            }
        }

        val adapter = ImageExtractionAdapter(fakeVisionBridge)
        assertTrue(adapter.supports("image/png", "png"))

        val result = adapter.extract(context, Uri.parse("content://img/1"), "img_1")
        assertTrue(result.success)
        assertTrue(result.content!!.text.contains("Amount Due: Rs 2450"))
    }

    // 17. Image Extraction Adapter Graceful Fallback
    @Test
    fun image_extraction_graceful_fallback() = runBlocking {
        val adapter = ImageExtractionAdapter(null)
        val result = adapter.extract(context, Uri.parse("content://img/2"), "img_2")
        assertTrue(result.success)
        assertEquals("", result.content!!.text)
        assertTrue(result.content!!.warnings.isNotEmpty())
    }

    // 18. Hybrid Search Engine Search
    @Test
    fun hybrid_search_engine_finds_item() = runBlocking {
        val searchEngine = NexusFileHybridSearchEngine()
        val item1 = SemanticFileIndexItem(
            metadata = FileMetadata("1", "content://1", "inverter_warranty.pdf", "pdf", "application/pdf"),
            chunks = listOf(FileContentChunk("c1", "1", "Inverter 5 year warranty terms and service center.", chunkIndex = 0))
        )
        val item2 = SemanticFileIndexItem(
            metadata = FileMetadata("2", "content://2", "recipe_book.pdf", "pdf", "application/pdf"),
            chunks = listOf(FileContentChunk("c2", "2", "Delicious paneer butter masala recipe.", chunkIndex = 0))
        )

        val query = FileSearchQuery("warranty inverter")
        val results = searchEngine.search(query, listOf(item1, item2))

        assertTrue(results.isNotEmpty())
        assertEquals("1", results.first().fileId)
    }

    // 19. Hybrid Search Engine Temporal Filtering
    @Test
    fun hybrid_search_engine_temporal_filtering() = runBlocking {
        val now = System.currentTimeMillis()
        val oldItem = SemanticFileIndexItem(
            metadata = FileMetadata("old", "content://old", "flight_ticket.pdf", "pdf", "application/pdf", lastModified = now - 1000000L)
        )
        val newItem = SemanticFileIndexItem(
            metadata = FileMetadata("new", "content://new", "flight_ticket.pdf", "pdf", "application/pdf", lastModified = now)
        )

        val searchEngine = NexusFileHybridSearchEngine()
        val query = FileSearchQuery("flight ticket", temporalFrom = now - 5000L)
        val results = searchEngine.search(query, listOf(oldItem, newItem))

        assertEquals(1, results.size)
        assertEquals("new", results.first().fileId)
    }

    // 20. Relationship Engine Builds Candidates
    @Test
    fun relationship_engine_builds_candidates() = runBlocking {
        val engine = NexusFileRelationshipEngine(null)
        val item = SemanticFileIndexItem(
            metadata = FileMetadata("doc_1", "content://doc1", "Contract.docx"),
            extractedEntities = listOf(
                ExtractedEntity(text = "Ramesh Kumar", type = "PERSON", confidence = 0.9f),
                ExtractedEntity(text = "Solar Inverter", type = "CONCEPT", confidence = 0.8f)
            )
        )

        val candidates = engine.buildCandidates(item)
        assertEquals(2, candidates.size)
        assertEquals("CREATED_BY", candidates[0].relationshipType)
        assertEquals("MENTIONED_IN", candidates[1].relationshipType)
    }

    // 21. Relationship Engine Publishes to Knowledge Bridge
    @Test
    fun relationship_engine_publishes_to_bridge() = runBlocking {
        var publishedCount = 0
        val fakeBridge = object : ExistingKnowledgeFileBridge {
            override suspend fun createCandidateRelationship(
                fileId: String,
                entityText: String,
                relationshipType: String,
                confidence: Float,
                evidence: String
            ) {
                publishedCount++
            }
        }

        val engine = NexusFileRelationshipEngine(fakeBridge)
        val item = SemanticFileIndexItem(
            metadata = FileMetadata("doc_1", "content://doc1", "Contract.docx"),
            extractedEntities = listOf(
                ExtractedEntity(text = "Ramesh Kumar", type = "PERSON", confidence = 0.9f)
            )
        )

        val candidates = engine.buildCandidates(item)
        engine.publish(candidates)
        assertEquals(1, publishedCount)
    }

    // 22. Context Adapter Updates and Bounds
    @Test
    fun context_adapter_bounds_data() {
        val adapter = NexusFileContextAdapter()
        val files = (1..25).map {
            FileMetadata(fileId = "f_$it", uri = "content://$it", name = "file_$it.txt")
        }

        adapter.update(
            recentFiles = files,
            queriedFiles = files,
            topMatches = emptyList(),
            activeFile = files.first()
        )

        val ctx = adapter.current()
        assertEquals(10, ctx.recentFiles.size)
        assertEquals(10, ctx.queriedFiles.size)
        assertEquals("f_1", ctx.activeFile?.fileId)
    }

    // 23. NexusContext Integration
    @Test
    fun nexus_context_holds_file_intelligence() {
        val fileContext = FileIntelligenceContext(
            recentFiles = listOf(FileMetadata("1", "content://1", "report.pdf")),
            activeFile = FileMetadata("1", "content://1", "report.pdf")
        )

        val nexusContext = NexusContext(
            inputChannel = InputChannel.TEXT,
            userInput = "find my report",
            fileIntelligenceContext = fileContext
        )

        assertNotNull(nexusContext.fileIntelligenceContext)
        assertEquals("report.pdf", nexusContext.fileIntelligenceContext?.activeFile?.name)
    }

    // 24. Vision Regression Check
    @Test
    fun vision_core_regression_check() {
        val visualContext = VisualContext(
            frameId = "vf_1",
            source = VisionSource.SCREEN,
            description = "Settings Screen",
            visibleText = "Wi-Fi, Bluetooth, Display",
            detectedObjects = emptyList(),
            detectedLabels = emptyList(),
            barcodes = emptyList(),
            timestamp = System.currentTimeMillis()
        )
        assertEquals("Settings Screen", visualContext.description)
    }

    // 25. Knowledge Core Regression Check
    @Test
    fun knowledge_core_regression_check() {
        val entity = KnowledgeEntity(
            id = "ke_1",
            type = KnowledgeEntityType.FILE,
            canonicalName = "warranty.pdf",
            source = KnowledgeSource.FILE
        )
        assertEquals(KnowledgeEntityType.FILE, entity.type)
    }

    // 26. Predictive Core Regression Check
    @Test
    fun predictive_core_regression_check() {
        val predContext = PredictiveContext(
            predictions = emptyList(),
            internalHints = emptyList(),
            proactiveCandidates = emptyList()
        )
        assertTrue(predContext.predictions.isEmpty())
    }

    // 27. Situational Awareness Regression Check
    @Test
    fun situational_awareness_regression_check() {
        val sitContext = SituationalContext()
        assertEquals(com.pantham.nexus.situational.model.SituationState.UNKNOWN, sitContext.activeState)
    }
}
