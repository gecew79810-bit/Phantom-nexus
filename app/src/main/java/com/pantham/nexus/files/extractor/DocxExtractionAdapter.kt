package com.pantham.nexus.files.extractor

import android.content.Context
import android.net.Uri
import com.pantham.nexus.files.model.ExtractedFileContent
import com.pantham.nexus.files.model.FileContentExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class DocxExtractionAdapter : ContentExtractionAdapter {

    override fun supports(
        mimeType: String?,
        extension: String?
    ): Boolean {

        return mimeType ==
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ||
            extension?.trimStart('.')?.lowercase() == "docx"
    }

    override suspend fun extract(
        context: Context,
        uri: Uri,
        fileId: String,
        metadata: Map<String, String>
    ): FileContentExtractionResult =
        withContext(Dispatchers.IO) {

            try {

                val input =
                    context.contentResolver.openInputStream(uri)
                        ?: return@withContext FileContentExtractionResult(
                            success = false,
                            error = "Unable to open DOCX URI."
                        )

                val textBuilder = StringBuilder()

                input.use { source ->

                    ZipInputStream(
                        source.buffered()
                    ).use { zip ->

                        while (true) {

                            val entry = zip.nextEntry
                                ?: break

                            if (
                                entry.name ==
                                "word/document.xml"
                            ) {

                                val factory =
                                    DocumentBuilderFactory
                                        .newInstance()

                                factory.isNamespaceAware = true

                                val document =
                                    factory
                                        .newDocumentBuilder()
                                        .parse(zip)

                                val nodes =
                                    document.getElementsByTagName(
                                        "w:t"
                                    )

                                for (i in 0 until nodes.length) {
                                    textBuilder
                                        .append(
                                            nodes
                                                .item(i)
                                                .textContent
                                        )
                                        .append(' ')
                                }

                                break
                            }
                        }
                    }
                }

                FileContentExtractionResult(
                    success = true,
                    content = ExtractedFileContent(
                        fileId = fileId,
                        text = textBuilder.toString().trim(),
                        metadata = metadata
                    )
                )

            } catch (t: Throwable) {

                FileContentExtractionResult(
                    success = false,
                    error = t.message ?: "DOCX extraction failed."
                )
            }
        }
}
