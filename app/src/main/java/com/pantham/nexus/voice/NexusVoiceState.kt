package com.pantham.nexus.voice

/**
 * Canonical voice interaction state for Pantham Nexus.
 */
enum class NexusVoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}
