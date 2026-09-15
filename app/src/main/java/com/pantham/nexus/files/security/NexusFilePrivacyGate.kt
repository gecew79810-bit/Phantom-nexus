package com.pantham.nexus.files.security

import android.content.Context
import android.net.Uri
import com.pantham.nexus.files.model.FileContentChunk

class NexusFilePrivacyGate {

    private val blockedSchemes =
        setOf(
            "file",
            "ftp",
            "smb"
        )

    fun canAccess(
        context: Context,
        uri: Uri
    ): Boolean {

        val scheme =
            uri.scheme?.lowercase()
                ?: return false

        if (scheme in blockedSchemes) {
            return false
        }

        return try {
            context.contentResolver
                .getType(uri)
            true
        } catch (_: Throwable) {
            scheme == "content" || scheme == "android.resource"
        }
    }

    fun sanitizeText(
        text: String
    ): String {

        return text
            .replace(
                Regex(
                    "(?i)(password|passwd|token|api[_-]?key|secret)\\s*[:=]\\s*\\S+"
                ),
                "$1: [REDACTED]"
            )
            .replace(
                Regex(
                    "\\b[A-Za-z0-9+/]{32,}={0,2}\\b"
                ),
                "[REDACTED_TOKEN]"
            )
            .take(100_000)
    }

    fun sanitizeChunk(
        chunk: FileContentChunk
    ): FileContentChunk {

        return chunk.copy(
            text = sanitizeText(
                chunk.text
            )
        )
    }
}
