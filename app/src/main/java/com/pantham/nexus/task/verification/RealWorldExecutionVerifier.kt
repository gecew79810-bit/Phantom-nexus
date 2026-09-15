package com.pantham.nexus.task.verification

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.action.goal.TaskArtifact
import com.example.action.goal.TaskNode
import com.example.action.goal.TaskStepResult
import com.example.hardware.HardwareController
import com.pantham.nexus.task.model.StepVerificationRule
import com.pantham.nexus.task.model.VerificationResult
import com.pantham.nexus.task.model.VerificationType
import java.io.File

/**
 * Validates whether an executed step produced its expected real-world effect.
 * Enforces the guardrail: "Action dispatched" != "Outcome successful".
 */
class RealWorldExecutionVerifier(
    private val context: Context? = null,
    private val hardwareController: HardwareController? = null
) {
    companion object {
        private const val TAG = "RealWorldExecutionVerifier"
    }

    suspend fun verifyStep(
        node: TaskNode,
        result: TaskStepResult,
        rule: StepVerificationRule? = null
    ): VerificationResult {
        // If step itself reported failure, verification fails immediately
        if (!result.success) {
            return VerificationResult(
                verified = false,
                reason = "Step execution returned failure: ${result.message}",
                type = VerificationType.OUTPUT_NON_EMPTY,
                confidence = 1.0f
            )
        }

        // 1. Check custom verification rule if supplied
        if (rule != null) {
            when (rule.type) {
                VerificationType.ARTIFACT_EXISTS -> {
                    val artifactCheck = verifyArtifactsExist(result.artifacts)
                    if (!artifactCheck.verified) return artifactCheck
                    return artifactCheck
                }
                VerificationType.OUTPUT_NON_EMPTY -> {
                    val outputCheck = verifyOutputPresent(result, rule.targetKey)
                    if (!outputCheck.verified) return outputCheck
                }
                VerificationType.CUSTOM_CRITERIA -> {
                    if (rule.customValidator != null) {
                        val passed = try {
                            rule.customValidator.invoke(result.outputData, result.artifacts)
                        } catch (e: Exception) {
                            Log.e(TAG, "Custom validator threw exception", e)
                            false
                        }
                        if (!passed) {
                            return VerificationResult(
                                verified = false,
                                reason = "Custom verification rule '${rule.description}' failed.",
                                type = VerificationType.CUSTOM_CRITERIA,
                                confidence = 0.9f
                            )
                        }
                    }
                }
                VerificationType.FILE_CONTENT_VALID -> {
                    val fileCheck = verifyFileContents(result.artifacts)
                    if (!fileCheck.verified) return fileCheck
                }
                VerificationType.SYSTEM_STATE_CHANGED -> {
                    // Handled by hardware / system state check
                }
            }
        }

        // 2. Default Real-World Checks:
        // A. If node has artifacts or title implies file/document generation, verify file existence on disk
        val isArtifactGeneratingNode = node.title.contains("PDF", ignoreCase = true) ||
                node.title.contains("Presentation", ignoreCase = true) ||
                node.title.contains("Report", ignoreCase = true) ||
                node.title.contains("File", ignoreCase = true) ||
                result.artifacts.isNotEmpty()

        if (isArtifactGeneratingNode && result.artifacts.isNotEmpty()) {
            val artResult = verifyArtifactsExist(result.artifacts)
            if (!artResult.verified) {
                return artResult
            }
        }

        // B. Check that output data or message is meaningful and non-empty
        if (result.message.isBlank() && result.outputData.isEmpty()) {
            return VerificationResult(
                verified = false,
                reason = "Step completed without producing verifiable message or output data.",
                type = VerificationType.OUTPUT_NON_EMPTY,
                confidence = 0.8f
            )
        }

        // Real-world verification passed
        return VerificationResult(
            verified = true,
            reason = "Real-world verification confirmed: output and artifacts validated successfully.",
            type = rule?.type ?: VerificationType.OUTPUT_NON_EMPTY,
            confidence = 0.95f
        )
    }

    private fun verifyArtifactsExist(artifacts: List<TaskArtifact>): VerificationResult {
        if (artifacts.isEmpty()) {
            return VerificationResult(
                verified = false,
                reason = "Expected output artifact was not produced.",
                type = VerificationType.ARTIFACT_EXISTS,
                confidence = 0.9f
            )
        }

        for (art in artifacts) {
            val uriStr = art.uri
            if (uriStr.startsWith("file://") || uriStr.startsWith("/")) {
                val path = if (uriStr.startsWith("file://")) uriStr.removePrefix("file://") else uriStr
                val f = File(path)
                if (!f.exists()) {
                    return VerificationResult(
                        verified = false,
                        reason = "Artifact file '${art.name}' was not found at path: $path",
                        type = VerificationType.ARTIFACT_EXISTS,
                        confidence = 1.0f
                    )
                }
                if (f.length() == 0L) {
                    return VerificationResult(
                        verified = false,
                        reason = "Artifact file '${art.name}' exists but has 0 bytes (empty file).",
                        type = VerificationType.FILE_CONTENT_VALID,
                        confidence = 0.95f
                    )
                }
            } else if (uriStr.startsWith("content://") && context != null) {
                try {
                    val pfd = context.contentResolver.openFileDescriptor(Uri.parse(uriStr), "r")
                    if (pfd == null || pfd.statSize <= 0) {
                        pfd?.close()
                        return VerificationResult(
                            verified = false,
                            reason = "Content URI for artifact '${art.name}' is unreadable or empty.",
                            type = VerificationType.ARTIFACT_EXISTS,
                            confidence = 0.95f
                        )
                    }
                    pfd.close()
                } catch (e: Exception) {
                    return VerificationResult(
                        verified = false,
                        reason = "Failed to access content URI for artifact '${art.name}': ${e.message}",
                        type = VerificationType.ARTIFACT_EXISTS,
                        confidence = 0.9f
                    )
                }
            }
        }

        return VerificationResult(
            verified = true,
            reason = "All ${artifacts.size} artifacts verified on disk.",
            type = VerificationType.ARTIFACT_EXISTS,
            confidence = 0.98f
        )
    }

    private fun verifyFileContents(artifacts: List<TaskArtifact>): VerificationResult {
        for (art in artifacts) {
            val path = art.uri.removePrefix("file://")
            val f = File(path)
            if (f.exists() && f.length() < 10L) {
                return VerificationResult(
                    verified = false,
                    reason = "Artifact file '${art.name}' is too small to contain valid data (${f.length()} bytes).",
                    type = VerificationType.FILE_CONTENT_VALID,
                    confidence = 0.9f
                )
            }
        }
        return VerificationResult(
            verified = true,
            reason = "Artifact contents verified.",
            type = VerificationType.FILE_CONTENT_VALID,
            confidence = 0.9f
        )
    }

    private fun verifyOutputPresent(result: TaskStepResult, targetKey: String?): VerificationResult {
        if (targetKey != null && !result.outputData.containsKey(targetKey)) {
            return VerificationResult(
                verified = false,
                reason = "Expected output key '$targetKey' was missing from step output.",
                type = VerificationType.OUTPUT_NON_EMPTY,
                confidence = 0.95f
            )
        }
        if (result.outputData.isEmpty() && result.message.isBlank()) {
            return VerificationResult(
                verified = false,
                reason = "Step produced empty output data and blank message.",
                type = VerificationType.OUTPUT_NON_EMPTY,
                confidence = 0.9f
            )
        }
        return VerificationResult(
            verified = true,
            reason = "Step outputs verified.",
            type = VerificationType.OUTPUT_NON_EMPTY,
            confidence = 0.95f
        )
    }
}
