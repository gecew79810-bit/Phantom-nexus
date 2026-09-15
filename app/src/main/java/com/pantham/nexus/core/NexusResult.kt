package com.pantham.nexus.core

sealed interface NexusResult {
    data class Success(val message: String) : NexusResult
    data class NeedsPermission(val explanation: String) : NexusResult
    data class NeedsConfirmation(val actionDescription: String) : NexusResult
    data class Blocked(val reason: String) : NexusResult
    data class Failed(val reason: String) : NexusResult
}
