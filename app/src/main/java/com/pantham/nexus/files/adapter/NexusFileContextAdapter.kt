package com.pantham.nexus.files.adapter

import com.pantham.nexus.files.model.FileIntelligenceContext
import com.pantham.nexus.files.model.FileMetadata
import com.pantham.nexus.files.model.FileSearchResult

class NexusFileContextAdapter {

    private var context =
        FileIntelligenceContext()

    fun update(
        recentFiles: List<FileMetadata>,
        queriedFiles: List<FileMetadata>,
        topMatches: List<FileSearchResult>,
        activeFile: FileMetadata?
    ) {

        context =
            FileIntelligenceContext(
                recentFiles =
                    recentFiles.take(10),

                queriedFiles =
                    queriedFiles.take(10),

                topMatches =
                    topMatches.take(10),

                activeFile =
                    activeFile
            )
    }

    fun current():
        FileIntelligenceContext =
        context

    fun getFileIntelligenceContext(activeQuery: String? = null):
        FileIntelligenceContext =
        context
}
