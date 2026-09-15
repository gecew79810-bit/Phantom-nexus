package com.pantham.nexus.intelligence.persistence

import com.pantham.nexus.intelligence.model.TaskCheckpoint
import com.pantham.nexus.intelligence.model.TaskStatus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface CheckpointStore {

    suspend fun save(
        checkpoint: TaskCheckpoint
    )

    suspend fun load(
        taskId: String
    ): TaskCheckpoint?

    suspend fun delete(
        taskId: String
    )
}

class FileCheckpointStore(
    private val directory: File
) : CheckpointStore {

    init {
        directory.mkdirs()
    }

    override suspend fun save(
        checkpoint: TaskCheckpoint
    ) = withContext(Dispatchers.IO) {

        val json =
            JSONObject().apply {

                put(
                    "taskId",
                    checkpoint.taskId
                )

                put(
                    "status",
                    checkpoint.status.name
                )

                put(
                    "completed",
                    JSONArray(
                        checkpoint.completedNodeIds.toList()
                    )
                )

                put(
                    "failed",
                    JSONArray(
                        checkpoint.failedNodeIds.toList()
                    )
                )

                put(
                    "pending",
                    JSONArray(
                        checkpoint.pendingNodeIds.toList()
                    )
                )

                put(
                    "updatedAt",
                    checkpoint.updatedAt
                )
            }

        File(
            directory,
            "${checkpoint.taskId}.json"
        ).writeText(
            json.toString()
        )
    }

    override suspend fun load(
        taskId: String
    ): TaskCheckpoint? =
        withContext(Dispatchers.IO) {

            val file =
                File(
                    directory,
                    "$taskId.json"
                )

            if (!file.exists()) {
                return@withContext null
            }

            val json =
                JSONObject(
                    file.readText()
                )

            TaskCheckpoint(
                taskId =
                    json.getString("taskId"),

                status =
                    TaskStatus.valueOf(
                        json.getString("status")
                    ),

                completedNodeIds =
                    json
                        .getJSONArray("completed")
                        .toStringSet(),

                failedNodeIds =
                    json
                        .getJSONArray("failed")
                        .toStringSet(),

                pendingNodeIds =
                    json
                        .getJSONArray("pending")
                        .toStringSet(),

                updatedAt =
                    json.getLong("updatedAt")
            )
        }

    override suspend fun delete(
        taskId: String
    ): Unit = withContext(Dispatchers.IO) {

        File(
            directory,
            "$taskId.json"
        ).delete()
        Unit
    }

    private fun JSONArray.toStringSet():
            Set<String> {

        val result =
            mutableSetOf<String>()

        for (i in 0 until length()) {
            result += getString(i)
        }

        return result
    }
}
