package com.example.action

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.ai.ActionStatus
import com.example.ai.AgentType
import com.example.ai.PendingAction
import com.example.ai.RiskLevel
import com.example.ai.TaskItem
import com.example.bridge.AndroidSystemBridge
import com.example.data.audit.ActionAuditLogger
import com.example.data.repository.EncryptedMemoryWalletRepository
import com.example.hardware.HardwareActionResult
import com.example.hardware.HardwareController
import com.example.media.MaxMediaManager
import com.example.permission.CentralPermissionsManager
import com.example.security.ToolPermissionMatrix
import com.example.service.MaxAccessibilityService
import com.example.service.MaxNotificationBridge
import com.example.telecom.MaxCallManager
import com.example.voice.AssistantLanguage
import com.example.voice.TextToSpeechProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Central NexusActionRouter for Pantham Nexus.
 * Translates AI/Voice decisions into concrete Android adapter calls with verification,
 * action history tracking, and confirmation gates.
 */
class NexusActionRouter(
    private val context: Context,
    private val systemBridge: AndroidSystemBridge,
    private val hardwareController: HardwareController,
    private val mediaManager: MaxMediaManager,
    private val memoryRepository: EncryptedMemoryWalletRepository,
    private val callManager: MaxCallManager?,
    private val ttsProvider: TextToSpeechProvider?,
    private val onTaskUpdated: (TaskItem) -> Unit,
    private val onRequestConfirmation: (PendingAction?) -> Unit,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    companion object {
        private const val TAG = "NexusActionRouter"
    }

    private fun currentTimeString(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    /**
     * Dispatches a NexusAction through the full execution pipeline:
     * Verification -> Permission -> Confirmation -> Execution -> Verification -> History -> TTS
     */
    suspend fun routeAction(
        action: NexusAction,
        bypassConfirmation: Boolean = false,
        language: AssistantLanguage = AssistantLanguage.HINDI
    ): NexusActionResult {
        val initialTask = TaskItem(
            id = action.actionId,
            time = currentTimeString(),
            title = action.title,
            isCompleted = false,
            progress = 10,
            agent = AgentType.SYSTEM,
            status = ActionStatus.RUNNING,
            details = "Initiating action: ${action.title}"
        )
        onTaskUpdated(initialTask)

        val startTime = System.currentTimeMillis()
        val toolMatrix = ToolPermissionMatrix.getInstance(context)
        val profile = toolMatrix.getProfileForAction(action)
        val needConfirmation = (action.requiresConfirmation || toolMatrix.requiresConfirmation(action)) && !bypassConfirmation

        // Check if confirmation is required and not bypassed
        if (needConfirmation) {
            val waitingTask = initialTask.copy(
                status = ActionStatus.WAITING_FOR_CONFIRMATION,
                details = "Awaiting user confirmation"
            )
            onTaskUpdated(waitingTask)

            val pending = PendingAction(
                id = action.actionId,
                title = "Confirm: ${action.title}",
                description = "${profile.description}. Do you confirm execution?",
                riskLevel = profile.defaultRiskLevel,
                onConfirm = {
                    onRequestConfirmation(null)
                    coroutineScope.launch {
                        routeAction(action, bypassConfirmation = true, language = language)
                    }
                },
                onCancel = {
                    onRequestConfirmation(null)
                    val failedTask = initialTask.copy(
                        status = ActionStatus.FAILED,
                        isCompleted = true,
                        details = "Action cancelled by user."
                    )
                    onTaskUpdated(failedTask)
                    ActionAuditLogger.getInstance(context).logAction(
                        actionId = action.actionId,
                        feature = action.javaClass.simpleName,
                        command = action.title,
                        selectedTool = profile.toolName,
                        requiredPermission = profile.requiredPermissions.joinToString().ifBlank { "NONE" },
                        confirmationState = "USER_CANCELLED",
                        result = "CANCELLED",
                        failureReason = "User rejected confirmation",
                        executionDurationMs = System.currentTimeMillis() - startTime
                    )
                }
            )
            onRequestConfirmation(pending)

            return NexusActionResult(
                actionId = action.actionId,
                success = false,
                message = "Waiting for user confirmation",
                status = ActionStatus.WAITING_FOR_CONFIRMATION,
                speechFeedback = if (language == AssistantLanguage.HINDI) "पुष्टि की आवश्यकता है।" else "Confirmation required."
            )
        }

        // Execute action with safe retry policy (only retry recoverable non-destructive operations)
        var result = executeInternal(action, language)
        if (!result.success && isRecoverable(action, result)) {
            Log.i(TAG, "Transient issue on safe operation, retrying with backoff...")
            delay(350)
            result = executeInternal(action, language)
        }

        val duration = System.currentTimeMillis() - startTime
        ActionAuditLogger.getInstance(context).logAction(
            actionId = action.actionId,
            feature = action.javaClass.simpleName,
            command = action.title,
            selectedTool = profile.toolName,
            requiredPermission = profile.requiredPermissions.joinToString().ifBlank { "NONE" },
            confirmationState = if (bypassConfirmation) "MANUAL_CONFIRMED" else "AUTO_APPROVED",
            result = if (result.success) "SUCCESS" else "FAILED",
            failureReason = if (!result.success) result.message else null,
            executionDurationMs = duration
        )

        // Record final status in Action History
        val finalStatus = when {
            result.status == ActionStatus.WAITING_FOR_PERMISSION -> ActionStatus.WAITING_FOR_PERMISSION
            result.status == ActionStatus.WAITING_FOR_CONFIRMATION -> ActionStatus.WAITING_FOR_CONFIRMATION
            result.success -> ActionStatus.SUCCESS
            else -> ActionStatus.FAILED
        }
        val isDone = finalStatus == ActionStatus.SUCCESS || finalStatus == ActionStatus.FAILED
        val completedTask = initialTask.copy(
            isCompleted = isDone,
            progress = if (finalStatus == ActionStatus.SUCCESS) 100 else 50,
            status = finalStatus,
            details = result.message
        )
        onTaskUpdated(completedTask)

        // Provide vocal speech feedback if available
        result.speechFeedback?.let { speech ->
            ttsProvider?.speak(speech, language)
        }

        return result
    }

    private fun isRecoverable(action: NexusAction, result: NexusActionResult): Boolean {
        if (result.status == ActionStatus.WAITING_FOR_PERMISSION || result.status == ActionStatus.WAITING_FOR_CONFIRMATION) {
            return false
        }
        // Destructive operations (SMS, Calls, WhatsApp, Memory writes) MUST NEVER auto-retry
        return when (action) {
            is NexusAction.Sms, is NexusAction.PhoneCall, is NexusAction.WhatsApp, is NexusAction.Memory -> false
            else -> true
        }
    }

    private suspend fun executeInternal(
        action: NexusAction,
        language: AssistantLanguage
    ): NexusActionResult {
        return when (action) {
            is NexusAction.LaunchApp -> {
                val (success, status) = systemBridge.launchAppByNameOrPackage(action.appName)
                NexusActionResult(
                    actionId = action.actionId,
                    success = success,
                    message = status,
                    status = if (success) ActionStatus.SUCCESS else ActionStatus.FAILED,
                    speechFeedback = if (success) "Opening ${action.appName}" else "Could not open ${action.appName}"
                )
            }

            is NexusAction.ScreenNav -> {
                if (!MaxAccessibilityService.isConnected.value && !CentralPermissionsManager.hasAccessibilityPermission(context)) {
                    CentralPermissionsManager.openAccessibilitySettings(context)
                    return NexusActionResult(
                        actionId = action.actionId,
                        success = false,
                        message = "Accessibility service disabled. Opened settings.",
                        status = ActionStatus.WAITING_FOR_PERMISSION,
                        speechFeedback = "Please enable Accessibility service."
                    )
                }

                val success = when (action.navCommand) {
                    ScreenNavCommand.HOME -> MaxAccessibilityService.performHome()
                    ScreenNavCommand.BACK -> MaxAccessibilityService.performBack()
                    ScreenNavCommand.RECENTS -> MaxAccessibilityService.performRecents()
                    ScreenNavCommand.NOTIFICATIONS -> MaxAccessibilityService.performNotifications()
                }
                NexusActionResult(
                    actionId = action.actionId,
                    success = success,
                    message = "Navigation command ${action.navCommand} executed: $success",
                    status = if (success) ActionStatus.SUCCESS else ActionStatus.FAILED,
                    speechFeedback = null
                )
            }

            is NexusAction.Volume -> {
                val res = when (action.direction) {
                    VolumeDirection.INCREASE -> hardwareController.adjustVolume(AudioManager.ADJUST_RAISE)
                    VolumeDirection.DECREASE -> hardwareController.adjustVolume(AudioManager.ADJUST_LOWER)
                    VolumeDirection.SET_PERCENT -> hardwareController.setVolumePercent(action.percent ?: 50)
                    VolumeDirection.MUTE -> hardwareController.setMute(true)
                    VolumeDirection.UNMUTE -> hardwareController.setMute(false)
                }
                val (success, message, speech) = when (res) {
                    is HardwareActionResult.Success -> Triple(true, res.message, res.speechFeedback)
                    is HardwareActionResult.DirectIntent -> Triple(true, res.message, res.speechFeedback)
                    is HardwareActionResult.PermissionRequired -> Triple(false, res.message, res.speechFeedback)
                    is HardwareActionResult.Error -> Triple(false, res.error, res.speechFeedback)
                }
                NexusActionResult(
                    actionId = action.actionId,
                    success = success,
                    message = message,
                    status = if (success) ActionStatus.SUCCESS else ActionStatus.FAILED,
                    speechFeedback = speech
                )
            }

            is NexusAction.Media -> {
                val pair = when (action.mediaCommand) {
                    MediaCommand.PLAY -> mediaManager.play()
                    MediaCommand.PAUSE -> mediaManager.pause()
                    MediaCommand.NEXT -> mediaManager.skipToNext()
                    MediaCommand.PREVIOUS -> mediaManager.skipToPrevious()
                    MediaCommand.TOGGLE -> mediaManager.togglePlayPause()
                }
                NexusActionResult(
                    actionId = action.actionId,
                    success = pair.first,
                    message = pair.second,
                    status = if (pair.first) ActionStatus.SUCCESS else ActionStatus.FAILED,
                    speechFeedback = pair.second
                )
            }

            is NexusAction.Memory -> {
                handleMemoryAction(action, language)
            }

            is NexusAction.WhatsApp -> {
                val (success, status) = systemBridge.openWhatsAppChatOrShare(
                    phone = action.recipient,
                    message = action.message
                )
                NexusActionResult(
                    actionId = action.actionId,
                    success = success,
                    message = status,
                    status = if (success) ActionStatus.SUCCESS else ActionStatus.FAILED,
                    speechFeedback = status
                )
            }

            is NexusAction.Sms -> {
                if (!CentralPermissionsManager.hasSmsPermission(context)) {
                    return NexusActionResult(
                        actionId = action.actionId,
                        success = false,
                        message = "SMS permission not granted.",
                        status = ActionStatus.WAITING_FOR_PERMISSION,
                        speechFeedback = "SMS permission required."
                    )
                }
                val result = systemBridge.sendSmsDirect(action.phoneNumber, action.message)
                val success = result.first
                val status = result.second
                NexusActionResult(
                    actionId = action.actionId,
                    success = success,
                    message = status,
                    status = if (success) ActionStatus.SUCCESS else ActionStatus.FAILED,
                    speechFeedback = status
                )
            }

            is NexusAction.PhoneCall -> {
                if (!CentralPermissionsManager.hasPhonePermission(context)) {
                    return NexusActionResult(
                        actionId = action.actionId,
                        success = false,
                        message = "Call phone permission not granted.",
                        status = ActionStatus.WAITING_FOR_PERMISSION,
                        speechFeedback = "Phone permission required."
                    )
                }
                val result = systemBridge.initiateCall(action.contactOrNumber, requireDirectCall = true)
                val success = result.first
                val status = result.second
                NexusActionResult(
                    actionId = action.actionId,
                    success = success,
                    message = status,
                    status = if (success) ActionStatus.SUCCESS else ActionStatus.FAILED,
                    speechFeedback = status
                )
            }

            is NexusAction.OpenCalendar -> {
                val (success, status) = systemBridge.openCalendar()
                NexusActionResult(
                    actionId = action.actionId,
                    success = success,
                    message = status,
                    status = if (success) ActionStatus.SUCCESS else ActionStatus.FAILED,
                    speechFeedback = if (success) "Calendar opened." else "Could not open calendar."
                )
            }

            is NexusAction.ReadNotifications -> {
                val notifs = MaxNotificationBridge.notifications.value
                val message = if (notifs.isEmpty()) {
                    "No recent notifications."
                } else {
                    val summary = notifs.take(3).joinToString("; ") { "${it.appName} from ${it.sender}: ${it.text}" }
                    "Recent notifications: $summary"
                }
                NexusActionResult(
                    actionId = action.actionId,
                    success = true,
                    message = message,
                    status = ActionStatus.SUCCESS,
                    speechFeedback = message
                )
            }

            is NexusAction.Routine -> {
                executeRoutine(action, language)
            }
        }
    }

    private suspend fun handleMemoryAction(
        action: NexusAction.Memory,
        language: AssistantLanguage
    ): NexusActionResult {
        return when (action.opType) {
            MemoryOpType.REMEMBER -> {
                val value = action.value ?: ""
                memoryRepository.savePreference(action.key, value)
                memoryRepository.saveMemoryWalletItem(
                    title = action.key,
                    content = value,
                    tag = action.key.lowercase()
                )
                val speech = if (language == AssistantLanguage.HINDI) {
                    "मैंने याद रख लिया कि आपकी ${action.key} $value है।"
                } else {
                    "I have remembered that your ${action.key} is $value."
                }
                NexusActionResult(
                    actionId = action.actionId,
                    success = true,
                    message = "Stored preference: ${action.key} = $value",
                    status = ActionStatus.SUCCESS,
                    speechFeedback = speech
                )
            }

            MemoryOpType.RECALL -> {
                val pref = memoryRepository.getPreference(action.key)
                if (!pref.isNullOrBlank()) {
                    val speech = if (language == AssistantLanguage.HINDI) {
                        "आपकी ${action.key} $pref है।"
                    } else {
                        "Your ${action.key} is $pref."
                    }
                    NexusActionResult(
                        actionId = action.actionId,
                        success = true,
                        message = "Recalled preference: ${action.key} = $pref",
                        status = ActionStatus.SUCCESS,
                        speechFeedback = speech
                    )
                } else {
                    val items = memoryRepository.getWalletItemsByTag(action.key.lowercase()).firstOrNull()
                    if (!items.isNullOrEmpty()) {
                        val content = items.first().content
                        val speech = if (language == AssistantLanguage.HINDI) {
                            "आपकी ${action.key} $content है।"
                        } else {
                            "Your ${action.key} is $content."
                        }
                        NexusActionResult(
                            actionId = action.actionId,
                            success = true,
                            message = "Recalled from memory wallet: $content",
                            status = ActionStatus.SUCCESS,
                            speechFeedback = speech
                        )
                    } else {
                        val speech = if (language == AssistantLanguage.HINDI) {
                            "मुझे आपकी ${action.key} के बारे में कोई जानकारी नहीं मिली।"
                        } else {
                            "I don't have any record of your ${action.key} yet."
                        }
                        NexusActionResult(
                            actionId = action.actionId,
                            success = false,
                            message = "No record found for ${action.key}",
                            status = ActionStatus.SUCCESS,
                            speechFeedback = speech
                        )
                    }
                }
            }

            MemoryOpType.CLEAR_ALL -> {
                memoryRepository.clearAllMemoryWallet()
                NexusActionResult(
                    actionId = action.actionId,
                    success = true,
                    message = "All persistent memory cleared.",
                    status = ActionStatus.SUCCESS,
                    speechFeedback = "Memory wallet cleared."
                )
            }

            MemoryOpType.FORGET -> {
                memoryRepository.deletePreference(action.key)
                NexusActionResult(
                    actionId = action.actionId,
                    success = true,
                    message = "Forgot ${action.key}",
                    status = ActionStatus.SUCCESS,
                    speechFeedback = "Removed from memory."
                )
            }
        }
    }

    /**
     * Executes multi-step automations (e.g. Morning Routine: Open Calendar -> Read notifications -> Start media)
     * Verifies step order, handles delays, logs history.
     */
    private suspend fun executeRoutine(
        routine: NexusAction.Routine,
        language: AssistantLanguage
    ): NexusActionResult {
        Log.i(TAG, "Starting routine execution: ${routine.routineName} with ${routine.steps.size} steps")
        val executedSteps = mutableListOf<String>()

        for ((index, step) in routine.steps.withIndex()) {
            val stepTask = TaskItem(
                id = "${routine.actionId}_step_$index",
                time = currentTimeString(),
                title = "[${routine.routineName}] ${step.title}",
                isCompleted = false,
                progress = ((index.toFloat() / routine.steps.size) * 100).toInt(),
                agent = AgentType.SYSTEM,
                status = ActionStatus.RUNNING,
                details = "Executing step ${index + 1} of ${routine.steps.size}"
            )
            onTaskUpdated(stepTask)

            val stepResult = routeAction(step, bypassConfirmation = true, language = language)
            executedSteps.add("${step.title} -> ${stepResult.status}")

            if (!stepResult.success && step !is NexusAction.ReadNotifications) {
                // If a critical step fails, stop and report
                val failedTask = stepTask.copy(
                    status = ActionStatus.FAILED,
                    isCompleted = true,
                    details = "Routine aborted at step: ${step.title} (${stepResult.message})"
                )
                onTaskUpdated(failedTask)
                return NexusActionResult(
                    actionId = routine.actionId,
                    success = false,
                    message = "Routine failed at step: ${step.title}",
                    status = ActionStatus.FAILED,
                    speechFeedback = "Routine ${routine.routineName} encountered an issue at ${step.title}."
                )
            }

            delay(600) // Inter-step delay for graceful OS transitions
        }

        return NexusActionResult(
            actionId = routine.actionId,
            success = true,
            message = "Routine ${routine.routineName} completed successfully: ${executedSteps.joinToString(", ")}",
            status = ActionStatus.SUCCESS,
            speechFeedback = "Routine ${routine.routineName} completed."
        )
    }

    /**
     * Executes a NexusAction returning a clean polymorphic NexusResult
     */
    suspend fun execute(action: NexusAction): com.pantham.nexus.core.NexusResult {
        val res = routeAction(action, bypassConfirmation = false)
        return when (res.status) {
            ActionStatus.SUCCESS -> com.pantham.nexus.core.NexusResult.Success(res.message)
            ActionStatus.WAITING_FOR_PERMISSION -> com.pantham.nexus.core.NexusResult.NeedsPermission(res.message)
            ActionStatus.WAITING_FOR_CONFIRMATION -> com.pantham.nexus.core.NexusResult.NeedsConfirmation(res.message)
            ActionStatus.FAILED -> com.pantham.nexus.core.NexusResult.Failed(res.message)
            else -> if (res.success) {
                com.pantham.nexus.core.NexusResult.Success(res.message)
            } else {
                com.pantham.nexus.core.NexusResult.Failed(res.message)
            }
        }
    }
}
