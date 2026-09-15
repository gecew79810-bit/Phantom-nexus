package com.example.data.audit

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ActionAuditRecord(
    val actionId: String,
    val timestamp: String,
    val feature: String,
    val command: String,
    val selectedTool: String,
    val requiredPermission: String = "NONE",
    val confirmationState: String = "AUTO_APPROVED",
    val result: String, // "SUCCESS" or "FAILED"
    val failureReason: String? = null,
    val executionDurationMs: Long = 0L
)

class ActionAuditLogger private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("nexus_action_audit_log", Context.MODE_PRIVATE)
    private val records = mutableListOf<ActionAuditRecord>()
    private val _auditHistory = MutableStateFlow<List<ActionAuditRecord>>(emptyList())
    val auditHistory: StateFlow<List<ActionAuditRecord>> = _auditHistory.asStateFlow()

    init {
        loadPersistedRecords()
    }

    @Synchronized
    fun logAction(
        actionId: String,
        feature: String,
        command: String,
        selectedTool: String,
        requiredPermission: String = "NONE",
        confirmationState: String = "AUTO_APPROVED",
        result: String,
        failureReason: String? = null,
        executionDurationMs: Long = 0L
    ) {
        val sanitizedCommand = sanitizeSensitiveData(command)
        val record = ActionAuditRecord(
            actionId = actionId,
            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            feature = feature,
            command = sanitizedCommand,
            selectedTool = selectedTool,
            requiredPermission = requiredPermission,
            confirmationState = confirmationState,
            result = result,
            failureReason = failureReason,
            executionDurationMs = executionDurationMs
        )

        records.add(0, record)
        if (records.size > 50) {
            records.removeAt(records.size - 1)
        }
        _auditHistory.value = records.toList()
        persistRecords()
    }

    private fun sanitizeSensitiveData(input: String): String {
        return input.replace(Regex("""\b(?:\d{4}[ -]?){3}\d{4}\b"""), "[REDACTED_CARD]")
            .replace(Regex("""\b(?:\d{3}-\d{2}-\d{4})\b"""), "[REDACTED_SSN]")
            .replace(Regex("""(?i)(password|pin|secret)[:=]\s*\S+"""), "$1=[REDACTED]")
    }

    private fun persistRecords() {
        val arr = JSONArray()
        records.take(20).forEach { rec ->
            val obj = JSONObject().apply {
                put("actionId", rec.actionId)
                put("timestamp", rec.timestamp)
                put("feature", rec.feature)
                put("command", rec.command)
                put("selectedTool", rec.selectedTool)
                put("requiredPermission", rec.requiredPermission)
                put("confirmationState", rec.confirmationState)
                put("result", rec.result)
                put("failureReason", rec.failureReason ?: "")
                put("duration", rec.executionDurationMs)
            }
            arr.put(obj)
        }
        prefs.edit().putString("audit_records_json", arr.toString()).apply()
    }

    private fun loadPersistedRecords() {
        val json = prefs.getString("audit_records_json", null) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                records.add(
                    ActionAuditRecord(
                        actionId = obj.optString("actionId"),
                        timestamp = obj.optString("timestamp"),
                        feature = obj.optString("feature"),
                        command = obj.optString("command"),
                        selectedTool = obj.optString("selectedTool"),
                        requiredPermission = obj.optString("requiredPermission", "NONE"),
                        confirmationState = obj.optString("confirmationState", "AUTO_APPROVED"),
                        result = obj.optString("result"),
                        failureReason = obj.optString("failureReason").takeIf { it.isNotBlank() },
                        executionDurationMs = obj.optLong("duration", 0L)
                    )
                )
            }
            _auditHistory.value = records.toList()
        } catch (e: Exception) {
            // Ignored
        }
    }

    companion object {
        @Volatile
        private var instance: ActionAuditLogger? = null

        fun getInstance(context: Context): ActionAuditLogger {
            return instance ?: synchronized(this) {
                instance ?: ActionAuditLogger(context.applicationContext).also { instance = it }
            }
        }
    }
}
