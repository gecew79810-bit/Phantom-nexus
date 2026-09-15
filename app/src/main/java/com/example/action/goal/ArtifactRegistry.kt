package com.example.action.goal

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class ArtifactRegistry private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nexus_artifact_registry_prefs", Context.MODE_PRIVATE)

    private val artifacts = mutableListOf<TaskArtifact>()

    init {
        loadPersistedArtifacts()
    }

    companion object {
        @Volatile
        private var instance: ArtifactRegistry? = null

        fun getInstance(context: Context): ArtifactRegistry {
            return instance ?: synchronized(this) {
                instance ?: ArtifactRegistry(context.applicationContext).also { instance = it }
            }
        }
    }

    @Synchronized
    fun registerArtifact(artifact: TaskArtifact) {
        artifacts.removeAll { it.artifactId == artifact.artifactId }
        artifacts.add(0, artifact) // Most recent first
        persistArtifacts()
    }

    @Synchronized
    fun getRecentArtifacts(limit: Int = 10): List<TaskArtifact> {
        return artifacts.take(limit)
    }

    @Synchronized
    fun getArtifactById(artifactId: String): TaskArtifact? {
        return artifacts.find { it.artifactId == artifactId }
    }

    @Synchronized
    fun getArtifactsByTask(taskId: String): List<TaskArtifact> {
        return artifacts.filter { it.taskId == taskId }
    }

    /**
     * Resolves semantic references like "isko", "usko", "last one", "the presentation", "the pdf", "first file"
     */
    @Synchronized
    fun resolveReference(referenceText: String): TaskArtifact? {
        val lower = referenceText.lowercase().trim()
        if (artifacts.isEmpty()) return null

        // 1. Explicit recency markers
        if (lower.contains("last") || lower.contains("previous") || lower.contains("हालिया") ||
            lower.contains("akhiri") || lower.contains("पिछला") || lower.contains("isko") ||
            lower.contains("usko") || lower.contains("same") || lower.contains("wahi") ||
            lower.contains("वही") || lower.contains("यह") || lower.contains("it") ||
            lower.contains("us ") || lower.contains("uss ") || lower.contains("woh ") || lower.contains("wo ")
        ) {
            // Check if user specifically requested a type e.g. "last presentation" or "us presentation"
            when {
                lower.contains("presentation") || lower.contains("ppt") || lower.contains("slide") -> {
                    return artifacts.find { it.type == ArtifactType.PRESENTATION } ?: artifacts.firstOrNull()
                }
                lower.contains("pdf") -> {
                    return artifacts.find { it.type == ArtifactType.PDF } ?: artifacts.firstOrNull()
                }
                lower.contains("image") || lower.contains("photo") || lower.contains("tasveer") || lower.contains("तस्वीर") -> {
                    return artifacts.find { it.type == ArtifactType.IMAGE } ?: artifacts.firstOrNull()
                }
                lower.contains("report") || lower.contains("रिपोर्ट") -> {
                    return artifacts.find { it.type == ArtifactType.REPORT } ?: artifacts.firstOrNull()
                }
                lower.contains("spreadsheet") || lower.contains("excel") || lower.contains("sheet") -> {
                    return artifacts.find { it.type == ArtifactType.SPREADSHEET } ?: artifacts.firstOrNull()
                }
                else -> return artifacts.firstOrNull()
            }
        }

        // 2. Type-based search
        if (lower.contains("presentation") || lower.contains("ppt")) {
            return artifacts.find { it.type == ArtifactType.PRESENTATION }
        }
        if (lower.contains("pdf")) {
            return artifacts.find { it.type == ArtifactType.PDF }
        }
        if (lower.contains("image") || lower.contains("photo") || lower.contains("pic")) {
            return artifacts.find { it.type == ArtifactType.IMAGE }
        }
        if (lower.contains("sheet") || lower.contains("excel")) {
            return artifacts.find { it.type == ArtifactType.SPREADSHEET }
        }

        // 3. Exact or substring name match
        return artifacts.find { lower.contains(it.name.lowercase()) }
    }

    @Synchronized
    fun renameArtifact(artifactId: String, newName: String): Boolean {
        val index = artifacts.indexOfFirst { it.artifactId == artifactId }
        if (index != -1) {
            val old = artifacts[index]
            artifacts[index] = old.copy(name = newName)
            persistArtifacts()
            return true
        }
        return false
    }

    @Synchronized
    fun removeArtifact(artifactId: String): Boolean {
        val removed = artifacts.removeAll { it.artifactId == artifactId }
        if (removed) {
            persistArtifacts()
        }
        return removed
    }

    @Synchronized
    fun clear() {
        artifacts.clear()
        persistArtifacts()
    }

    private fun persistArtifacts() {
        val array = JSONArray()
        for (item in artifacts.take(50)) {
            val obj = JSONObject().apply {
                put("artifactId", item.artifactId)
                put("name", item.name)
                put("type", item.type.name)
                put("uri", item.uri)
                put("description", item.description)
                put("taskId", item.taskId)
                put("createdAt", item.createdAt)
            }
            array.put(obj)
        }
        prefs.edit().putString("persisted_artifacts", array.toString()).apply()
    }

    private fun loadPersistedMemories() {
        // no-op
    }

    private fun loadPersistedArtifacts() {
        val raw = prefs.getString("persisted_artifacts", null) ?: return
        try {
            val array = JSONArray(raw)
            artifacts.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                artifacts.add(
                    TaskArtifact(
                        artifactId = obj.getString("artifactId"),
                        name = obj.getString("name"),
                        type = ArtifactType.valueOf(obj.getString("type")),
                        uri = obj.getString("uri"),
                        description = obj.getString("description"),
                        taskId = obj.getString("taskId"),
                        createdAt = obj.getLong("createdAt")
                    )
                )
            }
        } catch (e: Exception) {
            // Clean slate on deserialization error
            artifacts.clear()
        }
    }
}
