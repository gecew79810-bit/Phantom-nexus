package com.example.action.goal

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class TaskCheckpointStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nexus_task_checkpoints_prefs", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var instance: TaskCheckpointStore? = null

        fun getInstance(context: Context): TaskCheckpointStore {
            return instance ?: synchronized(this) {
                instance ?: TaskCheckpointStore(context.applicationContext).also { instance = it }
            }
        }
    }

    @Synchronized
    fun saveCheckpoint(checkpoint: TaskCheckpoint) {
        val key = "checkpoint_${checkpoint.taskId}"
        val obj = JSONObject().apply {
            put("taskId", checkpoint.taskId)
            put("conversationId", checkpoint.conversationId)
            put("goal", checkpoint.goal)
            put("completedSteps", JSONArray(checkpoint.completedSteps))
            put("remainingSteps", JSONArray(checkpoint.remainingSteps))
            put("currentStep", checkpoint.currentStep ?: "")
            put("createdAt", checkpoint.createdAt)
            put("updatedAt", System.currentTimeMillis())
            put("safeResumeState", checkpoint.safeResumeState)
            put("isPaused", checkpoint.isPaused)
            put("isCancelled", checkpoint.isCancelled)

            val outputsObj = JSONObject()
            checkpoint.stepOutputs.forEach { (k, v) -> outputsObj.put(k, v) }
            put("stepOutputs", outputsObj)

            val depsObj = JSONObject()
            checkpoint.dependencies.forEach { (k, v) -> depsObj.put(k, JSONArray(v)) }
            put("dependencies", depsObj)
        }
        prefs.edit().putString(key, obj.toString()).apply()
        // Record active checkpoint pointer
        if (!checkpoint.isCancelled && checkpoint.remainingSteps.isNotEmpty()) {
            prefs.edit().putString("active_checkpoint_id", checkpoint.taskId).apply()
        } else {
            if (prefs.getString("active_checkpoint_id", null) == checkpoint.taskId) {
                prefs.edit().remove("active_checkpoint_id").apply()
            }
        }
    }

    @Synchronized
    fun getCheckpoint(taskId: String): TaskCheckpoint? {
        val raw = prefs.getString("checkpoint_$taskId", null) ?: return null
        return deserialize(raw)
    }

    @Synchronized
    fun getActiveCheckpoint(): TaskCheckpoint? {
        val activeId = prefs.getString("active_checkpoint_id", null) ?: return null
        return getCheckpoint(activeId)
    }

    @Synchronized
    fun markPaused(taskId: String, isPaused: Boolean = true) {
        val cp = getCheckpoint(taskId) ?: return
        saveCheckpoint(cp.copy(isPaused = isPaused, updatedAt = System.currentTimeMillis()))
    }

    @Synchronized
    fun markCancelled(taskId: String) {
        val cp = getCheckpoint(taskId) ?: return
        saveCheckpoint(
            cp.copy(
                isCancelled = true,
                isPaused = false,
                remainingSteps = emptyList(),
                updatedAt = System.currentTimeMillis()
            )
        )
        if (prefs.getString("active_checkpoint_id", null) == taskId) {
            prefs.edit().remove("active_checkpoint_id").apply()
        }
    }

    @Synchronized
    fun clearCheckpoint(taskId: String) {
        prefs.edit().remove("checkpoint_$taskId").apply()
        if (prefs.getString("active_checkpoint_id", null) == taskId) {
            prefs.edit().remove("active_checkpoint_id").apply()
        }
    }

    private fun deserialize(raw: String): TaskCheckpoint? {
        return try {
            val obj = JSONObject(raw)
            val completedArr = obj.getJSONArray("completedSteps")
            val completed = mutableListOf<String>()
            for (i in 0 until completedArr.length()) completed.add(completedArr.getString(i))

            val remainingArr = obj.getJSONArray("remainingSteps")
            val remaining = mutableListOf<String>()
            for (i in 0 until remainingArr.length()) remaining.add(remainingArr.getString(i))

            val outputsObj = obj.optJSONObject("stepOutputs") ?: JSONObject()
            val outputs = mutableMapOf<String, String>()
            outputsObj.keys().forEach { k -> outputs[k] = outputsObj.getString(k) }

            val depsObj = obj.optJSONObject("dependencies") ?: JSONObject()
            val deps = mutableMapOf<String, List<String>>()
            depsObj.keys().forEach { k ->
                val arr = depsObj.getJSONArray(k)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) list.add(arr.getString(i))
                deps[k] = list
            }

            TaskCheckpoint(
                taskId = obj.getString("taskId"),
                conversationId = obj.optString("conversationId", "CONV_DEFAULT"),
                goal = obj.getString("goal"),
                completedSteps = completed,
                remainingSteps = remaining,
                currentStep = obj.optString("currentStep").takeIf { it.isNotBlank() },
                stepOutputs = outputs,
                dependencies = deps,
                createdAt = obj.getLong("createdAt"),
                updatedAt = obj.getLong("updatedAt"),
                safeResumeState = obj.getBoolean("safeResumeState"),
                isPaused = obj.optBoolean("isPaused", false),
                isCancelled = obj.optBoolean("isCancelled", false)
            )
        } catch (e: Exception) {
            null
        }
    }
}
