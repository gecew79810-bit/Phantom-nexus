package com.pantham.nexus.files.adapter

import com.example.action.goal.ArtifactRegistry
import com.example.action.goal.TaskArtifact
import com.pantham.nexus.files.model.FileMetadata

class ArtifactRegistryFileAdapter(
    private val registry: ArtifactRegistry
) {

    fun getArtifacts(): List<TaskArtifact> {
        return registry.getRecentArtifacts(limit = 100)
    }

    fun toMetadata(
        artifact: TaskArtifact
    ): FileMetadata {

        return FileMetadata(
            fileId =
                artifact.artifactId,
            uri =
                artifact.uri,
            name =
                artifact.name,
            extension =
                extensionFromName(
                    artifact.name
                ),
            mimeType =
                mimeFromArtifactType(
                    artifact.type.name
                ),
            sizeBytes = null,
            lastModified =
                artifact.createdAt
        )
    }

    private fun extensionFromName(
        name: String
    ): String? {

        val dot =
            name.lastIndexOf('.')

        if (dot < 0 || dot == name.length - 1) {
            return null
        }

        return name
            .substring(dot + 1)
            .lowercase()
    }

    private fun mimeFromArtifactType(
        type: String
    ): String? {

        return when (
            type.uppercase()
        ) {

            "PDF" ->
                "application/pdf"

            "SPREADSHEET" ->
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

            "PRESENTATION" ->
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"

            "IMAGE" ->
                "image/*"

            else ->
                null
        }
    }
}
