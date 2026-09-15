package com.example.voice

enum class VoiceState {
    IDLE,
    LISTENING,
    THINKING,
    PLANNING,
    WAITING_FOR_PERMISSION,
    WAITING_FOR_CONFIRMATION,
    EXECUTING,
    VERIFYING,
    SPEAKING,
    SUCCESS,
    FAILED,
    ERROR,
    OFFLINE
}

typealias AssistantState = VoiceState

enum class AssistantLanguage(val displayName: String, val speechCode: String) {
    HINDI("हिंदी (Hindi)", "hi-IN"),
    HINGLISH("हिंग्लिश (Hinglish)", "hi-IN"),
    ENGLISH("English (India)", "en-IN")
}
