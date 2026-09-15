package com.pantham.nexus.files.extractor

import android.content.Context
import android.net.Uri
import com.pantham.nexus.files.model.ExtractedFileContent
import com.pantham.nexus.files.model.FileContentExtractionResult

interface ContentExtractionAdapter {

    suspend fun extract(
        context: Context,
        uri: Uri,
        fileId: String,
        metadata: Map<String, String> = emptyMap()
    ): FileContentExtractionResult

    fun supports(
        mimeType: String?,
        extension: String?
    ): Boolean
}
