package com.example.ai

enum class CapabilityNode {
    THINK,
    EXECUTE,
    SEARCH,
    ANALYZE,
    LEARN,
    AUTOMATE,
    NONE
}

enum class AgentType(val displayName: String, val role: String) {
    PLANNER("Planner Agent", "Deconstructing goals and structuring execution pipelines"),
    RESEARCH("Research Agent", "Aggregating intelligence and live web synthesis"),
    CODING("Coding Agent", "Code analysis, generation, and script validation"),
    VISION("Vision Agent", "Screen inspection and multimodal image synthesis"),
    FILE("File Agent", "Local storage and file management utilities"),
    CALENDAR("Calendar Agent", "Schedule management and event coordination"),
    COMMUNICATION("Communication Agent", "WhatsApp, SMS, and telephone telephony control"),
    AUTOMATION("Automation Agent", "Multi-step workflows and macro execution"),
    SYSTEM("System Agent", "Direct Android hardware and peripheral management"),
    MEMORY("Memory Agent", "Context retention and persistent preference indexing"),
    BROWSER("Browser Agent", "Navigating online resources and web intent dispatch")
}

data class AgentState(
    val type: AgentType,
    val isActive: Boolean,
    val lastAction: String = "Standby"
)

enum class ActionStatus {
    RUNNING,
    WAITING_FOR_PERMISSION,
    WAITING_FOR_CONFIRMATION,
    SUCCESS,
    FAILED
}

data class TaskItem(
    val id: String = "ACT_" + System.currentTimeMillis() + "_" + (1000..9999).random(),
    val time: String,
    val title: String,
    val isCompleted: Boolean = false,
    val progress: Int = 100,
    val agent: AgentType = AgentType.SYSTEM,
    val status: ActionStatus = if (isCompleted) ActionStatus.SUCCESS else ActionStatus.RUNNING,
    val details: String = ""
)

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class PendingAction(
    val id: String = System.currentTimeMillis().toString(),
    val title: String,
    val description: String,
    val riskLevel: RiskLevel,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit = {}
)

data class ConsoleMessage(
    val id: String = System.currentTimeMillis().toString(),
    val sender: String, // "USER" or "MAX"
    val text: String,
    val timestamp: String,
    val isVoice: Boolean = false
)
