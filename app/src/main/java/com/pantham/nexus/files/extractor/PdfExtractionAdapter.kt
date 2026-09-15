package com.pantham.nexus.files.extractor

import android.content.Context
import android.net.Uri
import com.pantham.nexus.files.model.ExtractedFileContent
import com.pantham.nexus.files.model.FileContentExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class PdfExtractionAdapter(
    private val maxBytes: Long = 50L * 1024L * 1024L
) : ContentExtractionAdapter {

    override fun supports(
        mimeType: String?,
        extension: String?
    ): Boolean {
        return mimeType == "application/pdf" ||
            extension?.trimStart('.')?.lowercase() == "pdf"
    }

    override suspend fun extract(
        context: Context,
        uri: Uri,
        fileId: String,
        metadata: Map<String, String>
    ): FileContentExtractionResult =
        withContext(Dispatchers.IO) {

            try {

                val descriptor =
                    context.contentResolver.openAssetFileDescriptor(uri, "r")

                val length = descriptor?.length ?: -1L

                if (length > maxBytes) {
                    descriptor?.close()

                    return@withContext FileContentExtractionResult(
                        success = false,
                        error = "PDF exceeds extraction limit."
                    )
                }

                descriptor?.close()

                val text = runExistingPdfExtractor(
                    context = context,
                    uri = uri
                )

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
                    error = t.message ?: "PDF extraction failed."
                )
            }
        }

    private fun runExistingPdfExtractor(
        context: Context,
        uri: Uri
    ): String {
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return ""
            extractPdfTextFromStream(stream)
        } catch (_: Throwable) {
            ""
        }
    }

    private fun extractPdfTextFromStream(stream: InputStream): String {
        return try {
            val buffer = ByteArray(64 * 1024)
            val bytesRead = stream.read(buffer)
            if (bytesRead <= 0) return ""
            val raw = String(buffer, 0, bytesRead, Charsets.ISO_8859_1)

            // Extract plain text blocks within PDF stream operators Tj or [ ... ] TJ
            val textBuilder = StringBuilder()
            val tjRegex = Regex("\\(([^\\)]+)\\)\\s*Tj")
            tjRegex.findAll(raw).forEach { match ->
                textBuilder.append(match.groupValues[1]).append(" ")
            }

            val tjArrayRegex = Regex("\\[([^\\]]+)\\]\\s*TJ")
            tjArrayRegex.findAll(raw).forEach { match ->
                val inner = match.groupValues[1]
                val subRegex = Regex("\\(([^\\)]+)\\)")
                subRegex.findAll(inner).forEach { sub ->
                    textBuilder.append(sub.groupValues[1]).append(" ")
                }
            }

            textBuilder.toString().trim()
        } catch (_: Throwable) {
            ""
        } finally {
            try { stream.close() } catch (_: Throwable) {}
        }
    }
}
