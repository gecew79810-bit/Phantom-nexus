package com.pantham.nexus.files.search

import com.pantham.nexus.files.model.FileSearchIntent
import com.pantham.nexus.files.model.FileSearchQuery
import com.pantham.nexus.files.model.RewrittenFileQuery

class NexusFileQueryRewriter {

    fun rewrite(
        query: FileSearchQuery
    ): RewrittenFileQuery {

        val original =
            query.queryText.trim()

        val normalized =
            original
                .lowercase()
                .replace(
                    Regex("[^\\p{L}\\p{N}\\s._-]"),
                    " "
                )
                .replace(
                    Regex("\\s+"),
                    " "
                )
                .trim()

        val stopWords = setOf(
            "the", "a", "an", "my", "me",
            "please", "find", "show", "give",
            "open", "that", "this", "file",
            "document", "pdf", "wali", "wala",
            "वो", "वाली", "वाला", "फाइल",
            "डॉक्यूमेंट", "ढूंढो", "दिखाओ", "kholo",
            "dikhana", "dikhao", "dhundo", "batao",
            "search", "check", "get", "mera", "meri", "mere",
            "woh", "wo", "wale", "ka", "ki", "ke", "karo", "hai", "tha", "thi"
        )

        val terms = normalized
            .split(" ")
            .filter {
                it.length >= 2 &&
                    it !in stopWords
            }

        val extension =
            when {
                normalized.contains("pdf") -> "pdf"
                normalized.contains("docx") ||
                    normalized.contains("word") -> "docx"
                normalized.contains("csv") -> "csv"
                normalized.contains("json") -> "json"
                normalized.contains("image") ||
                    normalized.contains("photo") ||
                    normalized.contains("picture") -> "image"
                else -> null
            }

        val mime =
            when (extension) {
                "pdf" -> "application/pdf"
                "docx" ->
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "csv" -> "text/csv"
                "json" -> "application/json"
                "image" -> "image/*"
                else -> null
            }

        val intent =
            when {
                normalized.startsWith("open ") || normalized.startsWith("kholo ") ->
                    FileSearchIntent.OPEN

                normalized.contains("compare") ->
                    FileSearchIntent.COMPARE

                normalized.contains("summar") ->
                    FileSearchIntent.SUMMARIZE

                normalized.contains("recent") ||
                    normalized.contains("latest") ||
                    normalized.contains("newest") ->
                    FileSearchIntent.RECENT

                normalized.contains("oldest") ->
                    FileSearchIntent.OLDEST

                normalized.contains("how much") ||
                    normalized.contains("what amount") ||
                    normalized.contains("where") ->
                    FileSearchIntent.LOCATE_INFORMATION

                extension != null ->
                    FileSearchIntent.BY_TYPE

                else ->
                    FileSearchIntent.FIND
            }

        return RewrittenFileQuery(
            original = original,
            terms = terms,
            normalizedTerms = terms,
            probableExtension = extension,
            probableMimeType = mime,
            temporalFrom = query.temporalFrom,
            temporalTo = query.temporalTo,
            entities = query.requiredEntities,
            intent = query.intent.takeUnless {
                it == FileSearchIntent.UNKNOWN
            } ?: intent
        )
    }
}
