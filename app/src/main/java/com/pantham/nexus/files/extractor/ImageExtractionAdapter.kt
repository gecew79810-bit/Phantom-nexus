package com.pantham.nexus.files.extractor

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.pantham.nexus.files.model.ExtractedFileContent
import com.pantham.nexus.files.model.FileContentExtractionResult
import com.pantham.nexus.vision.model.VisualContext
import com.pantham.nexus.vision.ocr.NexusTextRecognitionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ExistingVisionFileBridge {

    suspend fun analyzeImage(
        context: Context,
        uri: Uri
    ): VisualContext?
}

class DefaultVisionFileBridge(
    private val ocrEngine: NexusTextRecognitionEngine? = null
) : ExistingVisionFileBridge {

    override suspend fun analyzeImage(
        context: Context,
        uri: Uri
    ): VisualContext? = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val bitmap = BitmapFactory.decodeStream(stream)
            stream.close()
            if (bitmap == null) return@withContext null

            val engine = ocrEngine ?: NexusTextRecognitionEngine()
            val textBlocks = engine.recognize(bitmap)
            val fullText = textBlocks.joinToString("\n") { it.text }

            VisualContext(
                frameId = "file_${System.currentTimeMillis()}",
                source = com.pantham.nexus.vision.model.VisionSource.IMAGE_FILE,
                description = "Scanned document/image",
                visibleText = fullText,
                detectedObjects = emptyList(),
                detectedLabels = emptyList(),
                barcodes = emptyList(),
                timestamp = System.currentTimeMillis()
            )
        } catch (_: Throwable) {
            null
        }
    }
}

class ImageExtractionAdapter(
    private val visionBridge: ExistingVisionFileBridge?
) : ContentExtractionAdapter {

    override fun supports(
        mimeType: String?,
        extension: String?
    ): Boolean {

        if (mimeType?.startsWith("image/") == true) {
            return true
        }

        return extension
            ?.trimStart('.')
            ?.lowercase()
            ?.let {
                it in setOf(
                    "png",
                    "jpg",
                    "jpeg",
                    "webp",
                    "bmp"
                )
            } == true
    }

    override suspend fun extract(
        context: Context,
        uri: Uri,
        fileId: String,
        metadata: Map<String, String>
    ): FileContentExtractionResult =
        withContext(Dispatchers.IO) {

            try {

                val visual =
                    visionBridge?.analyzeImage(
                        context,
                        uri
                    )

                if (visual == null) {
                    return@withContext FileContentExtractionResult(
                        success = true,
                        content = ExtractedFileContent(
                            fileId = fileId,
                            text = "",
                            metadata = metadata,
                            warnings = listOf(
                                "Vision bridge unavailable."
                            )
                        )
                    )
                }

                val text =
                    buildString {

                        visual.visibleText
                            ?.take(100_000)
                            ?.let {
                                append(it)
                            }

                        if (
                            !visual.description
                                .isNullOrBlank()
                        ) {

                            append("\n")
                            append(
                                visual.description
                                    .take(2_000)
                            )
                        }
                    }

                FileContentExtractionResult(
                    success = true,
                    content = ExtractedFileContent(
                        fileId = fileId,
                        text = text,
                        metadata = metadata
                    )
                )

            } catch (t: Throwable) {

                FileContentExtractionResult(
                    success = false,
                    error = t.message ?: "Image extraction failed."
                )
            }
        }
}
