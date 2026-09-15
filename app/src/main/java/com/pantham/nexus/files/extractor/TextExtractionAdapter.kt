package com.pantham.nexus.files.extractor

import android.content.Context
import android.net.Uri
import com.pantham.nexus.files.model.ExtractedFileContent
import com.pantham.nexus.files.model.FileContentExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TextExtractionAdapter(
    private val maxBytes: Long = 5L * 1024L * 1024L
) : ContentExtractionAdapter {

    private val supportedExtensions = setOf(
        "txt", "md", "csv", "json", "log", "xml"
    )

    override fun supports(
        mimeType: String?,
        extension: String?
    ): Boolean {

        val ext = extension
            ?.trimStart('.')
            ?.lowercase()

        if (ext != null && ext in supportedExtensions) {
            return true
        }

        return mimeType?.startsWith("text/") == true ||
            mimeType == "application/json"
    }

    override suspend fun extract(
        context: Context,
        uri: Uri,
        fileId: String,
        metadata: Map<String, String>
    ): FileContentExtractionResult =
        withContext(Dispatchers.IO) {

            try {

                val resolver = context.contentResolver

                val size = resolver
                    .openAssetFileDescriptor(uri, "r")
                    ?.length

                if (size != null && size > maxBytes) {
                    return@withContext FileContentExtractionResult(
                        success = false,
                        error = "Text file exceeds extraction limit."
                    )
                }

                val stream = resolver.openInputStream(uri)
                    ?: return@withContext FileContentExtractionResult(
                        success = false,
                        error = "Unable to open file URI."
                    )

                stream.use {
                    val text = it
                        .bufferedReader()
                        .readText()
                        .take(maxBytes.toInt())

                    FileContentExtractionResult(
                        success = true,
                        content = ExtractedFileContent(
                            fileId = fileId,
                            text = text,
                            metadata = metadata
                        )
                    )
                }

            } catch (t: Throwable) {

                FileContentExtractionResult(
                    success = false,
                    error = t.message ?: "Text extraction failed."
                )
            }
        }
}
