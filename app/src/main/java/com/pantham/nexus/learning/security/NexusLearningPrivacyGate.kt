package com.pantham.nexus.learning.security

import com.pantham.nexus.learning.model.*

class NexusLearningPrivacyGate {

    private val sensitiveKeys =
        setOf(
            "password",
            "passcode",
            "otp",
            "pin",
            "token",
            "secret",
            "api_key",
            "apikey",
            "credit_card",
            "card_number",
            "cvv"
        )

    fun shouldLearn(
        signal: LearningSignal
    ): Boolean {

        val lowerKey = signal.key.lowercase()
        val tokens = lowerKey.split(Regex("[._-]")).toSet()

        if (
            sensitiveKeys.any { sensitive ->
                sensitive == lowerKey ||
                sensitive in tokens ||
                lowerKey.contains(sensitive)
            }
        ) {
            return false
        }

        if (signal.value.length > 1000) {
            return false
        }

        return true
    }

    fun sanitize(
        signal: LearningSignal
    ): LearningSignal? {

        if (!shouldLearn(signal)) {
            return null
        }

        val sanitizedValue =
            signal.value
                .replace(
                    Regex(
                        """(?i)(password|token|api[_-]?key|secret)\s*[:=]\s*\S+"""
                    ),
                    "$1=[REDACTED]"
                )
                .take(1000)

        return signal.copy(
            value = sanitizedValue
        )
    }
}
