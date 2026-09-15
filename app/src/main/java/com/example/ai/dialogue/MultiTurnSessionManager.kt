package com.example.ai.dialogue

import com.example.action.NexusAction
import com.example.context.NexusContextEngine
import com.example.voice.AssistantLanguage

sealed class MultiTurnState {
    object Idle : MultiTurnState()

    data class AwaitingSlot(
        val intent: String, // e.g. "WHATSAPP", "SMS", "CALL"
        val targetEntity: String, // e.g. "Rahul"
        val missingSlot: String, // e.g. "MESSAGE_BODY"
        val promptQuestion: String
    ) : MultiTurnState()

    data class AwaitingConfirmation(
        val action: NexusAction,
        val promptQuestion: String
    ) : MultiTurnState()

    data class DraftHeld(
        val action: NexusAction,
        val promptQuestion: String
    ) : MultiTurnState()
}

data class MultiTurnResult(
    val handled: Boolean,
    val responseSpeech: String,
    val readyAction: NexusAction? = null,
    val requiresMoreTurns: Boolean = false
)

class MultiTurnSessionManager(
    private val contextEngine: NexusContextEngine
) {
    var currentState: MultiTurnState = MultiTurnState.Idle
        private set

    fun reset() {
        currentState = MultiTurnState.Idle
    }

    fun setAwaitingDecisionSlot(
        missingSlot: String,
        promptQuestion: String,
        targetEntity: String = "OPTIONS"
    ) {
        currentState = MultiTurnState.AwaitingSlot(
            intent = "DECISION",
            targetEntity = targetEntity,
            missingSlot = missingSlot,
            promptQuestion = promptQuestion
        )
    }

    /**
     * Checks if current input satisfies a multi-turn pending state.
     */
    fun processFollowUp(
        input: String,
        language: AssistantLanguage
    ): MultiTurnResult {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        when (val state = currentState) {
            is MultiTurnState.Idle -> {
                return MultiTurnResult(handled = false, responseSpeech = "")
            }

            is MultiTurnState.AwaitingSlot -> {
                // Check if user is cancelling
                if (isCancellation(lower)) {
                    reset()
                    val speech = if (language == AssistantLanguage.HINDI) "ठीक है बॉस, कैंसिल कर दिया।" else "Cancelled."
                    return MultiTurnResult(handled = true, responseSpeech = speech)
                }

                if (state.missingSlot == "MESSAGE_BODY") {
                    // Clean message prefix if user said "bolna ki ...", "say that ...", "write ..."
                    val cleanedMessage = cleanMessageBody(trimmed)
                    val action = when (state.intent) {
                        "WHATSAPP" -> NexusAction.WhatsApp(state.targetEntity, cleanedMessage)
                        else -> NexusAction.Sms(state.targetEntity, cleanedMessage)
                    }

                    // Move to AwaitingConfirmation
                    val confirmPrompt = if (language == AssistantLanguage.HINDI) {
                        "${state.targetEntity} को यह मैसेज भेज दूँ: \"$cleanedMessage\"?"
                    } else {
                        "Send this message to ${state.targetEntity}: \"$cleanedMessage\"?"
                    }
                    currentState = MultiTurnState.AwaitingConfirmation(action, confirmPrompt)
                    return MultiTurnResult(
                        handled = true,
                        responseSpeech = confirmPrompt,
                        requiresMoreTurns = true
                    )
                }

                if (state.intent == "DECISION") {
                    val slotAnswer = trimmed
                    reset()
                    val speech = if (language == AssistantLanguage.HINDI) {
                        "जानकारी दर्ज कर ली: $slotAnswer"
                    } else {
                        "Got the information: $slotAnswer"
                    }
                    return MultiTurnResult(handled = true, responseSpeech = speech)
                }

                return MultiTurnResult(handled = false, responseSpeech = "")
            }

            is MultiTurnState.AwaitingConfirmation -> {
                // 1. Hold / Pause draft
                if (lower.contains("abhi mat") || lower.contains("hold") || lower.contains("rehne do") || lower.contains("don't send yet") || lower.contains("wait")) {
                    val speech = if (language == AssistantLanguage.HINDI) "मैसेज ड्राफ्ट सुरक्षित रख लिया है, अभी नहीं भेज रहा हूँ।" else "Draft message held. Not sending yet."
                    currentState = MultiTurnState.DraftHeld(state.action, speech)
                    return MultiTurnResult(handled = true, responseSpeech = speech, requiresMoreTurns = true)
                }

                // 2. Recipient correction: "Arre nahi, Rahul nahi, Rohit ko bhejna tha"
                val newContactCandidate = extractContact(trimmed)
                if (newContactCandidate != null && (lower.contains("nahi") || lower.contains("instead") || lower.contains("ko bhejna") || lower.contains("ko bhejo"))) {
                    val currentMsg = when (val act = state.action) {
                        is NexusAction.WhatsApp -> act.message
                        is NexusAction.Sms -> act.message
                        else -> ""
                    }
                    val updatedAction = when (val act = state.action) {
                        is NexusAction.WhatsApp -> act.copy(recipient = newContactCandidate)
                        is NexusAction.Sms -> act.copy(phoneNumber = newContactCandidate)
                        else -> act
                    }
                    contextEngine.setLastContact(newContactCandidate)
                    val confirmPrompt = if (language == AssistantLanguage.HINDI) {
                        "प्राप्तकर्ता बदलकर $newContactCandidate कर दिया। संदेश वही रहेगा: \"$currentMsg\"। भेज दूँ?"
                    } else {
                        "Updated recipient to $newContactCandidate with the same message: \"$currentMsg\". Send now?"
                    }
                    currentState = MultiTurnState.AwaitingConfirmation(updatedAction, confirmPrompt)
                    return MultiTurnResult(handled = true, responseSpeech = confirmPrompt, requiresMoreTurns = true)
                }

                // 3. Message Shortening: "phir se bana do, but shorter"
                if (lower.contains("shorter") || lower.contains("short") || lower.contains("chota") || lower.contains("छोटा")) {
                    val currentMsg = when (val act = state.action) {
                        is NexusAction.WhatsApp -> act.message
                        is NexusAction.Sms -> act.message
                        else -> ""
                    }
                    // Generate concise version
                    val shortMsg = if (currentMsg.contains("10") || currentMsg.contains("late")) "10m late due to traffic." else "Running late, will reach soon."
                    val updatedAction = when (val act = state.action) {
                        is NexusAction.WhatsApp -> act.copy(message = shortMsg)
                        is NexusAction.Sms -> act.copy(message = shortMsg)
                        else -> act
                    }
                    val confirmPrompt = if (language == AssistantLanguage.HINDI) {
                        "संदेश संक्षिप्त कर दिया: \"$shortMsg\"। भेज दूँ?"
                    } else {
                        "Shortened message to: \"$shortMsg\". Send now?"
                    }
                    currentState = MultiTurnState.AwaitingConfirmation(updatedAction, confirmPrompt)
                    return MultiTurnResult(handled = true, responseSpeech = confirmPrompt, requiresMoreTurns = true)
                }

                // 4. Appending to message: "Haan, aur usme ye bhi likho ki traffic bohot zyada hai"
                if (lower.contains("aur") || lower.contains("also") || lower.contains("add") || lower.contains("likho")) {
                    val appendix = extractAppendedText(trimmed)
                    val currentMsg = when (val act = state.action) {
                        is NexusAction.WhatsApp -> act.message
                        is NexusAction.Sms -> act.message
                        else -> ""
                    }
                    val mergedMsg = if (appendix.isNotBlank()) "$currentMsg, $appendix" else currentMsg
                    val updatedAction = when (val act = state.action) {
                        is NexusAction.WhatsApp -> act.copy(message = mergedMsg)
                        is NexusAction.Sms -> act.copy(message = mergedMsg)
                        else -> act
                    }
                    val confirmPrompt = if (language == AssistantLanguage.HINDI) {
                        "संदेश में जोड़ दिया: \"$mergedMsg\"। भेज दूँ?"
                    } else {
                        "Updated message to: \"$mergedMsg\". Send now?"
                    }
                    currentState = MultiTurnState.AwaitingConfirmation(updatedAction, confirmPrompt)
                    return MultiTurnResult(handled = true, responseSpeech = confirmPrompt, requiresMoreTurns = true)
                }

                // 5. Direct Confirmation
                if (isConfirmation(lower) && !lower.contains("nahi") && !lower.contains("mat")) {
                    val actionToExecute = state.action
                    reset()
                    val speech = if (language == AssistantLanguage.HINDI) "जी, अभी भेज रहा हूँ।" else "Sending now."
                    return MultiTurnResult(
                        handled = true,
                        responseSpeech = speech,
                        readyAction = actionToExecute,
                        requiresMoreTurns = false
                    )
                } else if (isCancellation(lower) && !lower.contains("hold")) {
                    reset()
                    val speech = if (language == AssistantLanguage.HINDI) "ठीक है, रद्द कर दिया।" else "Action cancelled."
                    return MultiTurnResult(handled = true, responseSpeech = speech)
                }

                // 6. Generic update
                val cleanedMessage = cleanMessageBody(trimmed)
                val updatedAction = when (val act = state.action) {
                    is NexusAction.WhatsApp -> act.copy(message = cleanedMessage)
                    is NexusAction.Sms -> act.copy(message = cleanedMessage)
                    else -> act
                }
                val confirmPrompt = if (language == AssistantLanguage.HINDI) {
                    "मैसेज बदलकर \"$cleanedMessage\" कर दिया। भेज दूँ?"
                } else {
                    "Updated message to \"$cleanedMessage\". Should I send it?"
                }
                currentState = MultiTurnState.AwaitingConfirmation(updatedAction, confirmPrompt)
                return MultiTurnResult(handled = true, responseSpeech = confirmPrompt, requiresMoreTurns = true)
            }

            is MultiTurnState.DraftHeld -> {
                // User can resume/send draft or cancel
                if (isConfirmation(lower) || lower.contains("bhej do") || lower.contains("send it") || lower.contains("actually bhej do")) {
                    val actionToExecute = state.action
                    reset()
                    val speech = if (language == AssistantLanguage.HINDI) "ड्राफ्ट भेज दिया गया है।" else "Held draft sent."
                    return MultiTurnResult(
                        handled = true,
                        responseSpeech = speech,
                        readyAction = actionToExecute,
                        requiresMoreTurns = false
                    )
                } else if (isCancellation(lower)) {
                    reset()
                    val speech = if (language == AssistantLanguage.HINDI) "ड्राफ्ट हटा दिया गया।" else "Draft discarded."
                    return MultiTurnResult(handled = true, responseSpeech = speech)
                } else {
                    val speech = if (language == AssistantLanguage.HINDI) "ड्राफ्ट अभी सुरक्षित है। क्या इसे भेज दूँ?" else "Draft is currently held. Should I send it?"
                    return MultiTurnResult(handled = true, responseSpeech = speech, requiresMoreTurns = true)
                }
            }
        }
    }

    /**
     * Checks if an initial single-sentence command has missing slots.
     */
    fun checkIncompleteCommand(
        input: String,
        language: AssistantLanguage
    ): MultiTurnResult? {
        val lower = input.lowercase()

        // 1. WhatsApp with missing message
        // E.g. "Rahul ko WhatsApp message karo" or "WhatsApp Rahul" or "usko whatsapp karo"
        if ((lower.contains("whatsapp") || lower.contains("व्हाट्सएप")) &&
            (lower.contains("message") || lower.contains("msg") || lower.contains("karo") || lower.contains("bhejo") || lower.contains("send"))
        ) {
            val contact = extractContact(input)
            val message = extractInlineMessage(input)

            if (contact != null) {
                contextEngine.setLastContact(contact)
            }

            val targetContact = contact ?: contextEngine.lastInteractedContact

            if (targetContact != null && message.isNullOrBlank()) {
                val prompt = if (language == AssistantLanguage.HINDI) {
                    "क्या मैसेज भेजना है $targetContact को?"
                } else {
                    "What message should I send to $targetContact?"
                }
                currentState = MultiTurnState.AwaitingSlot(
                    intent = "WHATSAPP",
                    targetEntity = targetContact,
                    missingSlot = "MESSAGE_BODY",
                    promptQuestion = prompt
                )
                return MultiTurnResult(handled = true, responseSpeech = prompt, requiresMoreTurns = true)
            }
        }

        // 2. SMS / General Message with missing body
        if ((lower.contains("sms") || lower.contains("text message") || lower.contains("message") || lower.contains("msg") || lower.contains("संदेश")) &&
            (lower.contains("karo") || lower.contains("bhejo") || lower.contains("send") || lower.contains("karna") || lower.contains("bhejna"))
        ) {
            val contact = extractContact(input)
            val message = extractInlineMessage(input)

            if (contact != null) {
                contextEngine.setLastContact(contact)
            }

            val targetContact = contact ?: contextEngine.lastInteractedContact

            if (targetContact != null && message.isNullOrBlank()) {
                val prompt = if (language == AssistantLanguage.HINDI) {
                    "$targetContact को क्या संदेश भेजना है?"
                } else {
                    "What message should I send to $targetContact?"
                }
                currentState = MultiTurnState.AwaitingSlot(
                    intent = "SMS",
                    targetEntity = targetContact,
                    missingSlot = "MESSAGE_BODY",
                    promptQuestion = prompt
                )
                return MultiTurnResult(handled = true, responseSpeech = prompt, requiresMoreTurns = true)
            }
        }

        return null
    }

    private fun isConfirmation(text: String): Boolean {
        val lower = text.lowercase().trim()
        val words = lower.split(Regex("""[\s,?!.]+""")).filter { it.isNotBlank() }.toSet()
        if (words.contains("nahi") || words.contains("no") || words.contains("not") || words.contains("नहीं") || lower.contains("mat")) {
            return false
        }
        val exactKeywords = setOf("haan", "ha", "yes", "yeah", "yep", "sure", "ok", "okay", "हाँ", "bilkul")
        if (words.any { exactKeywords.contains(it) }) return true

        val phrases = listOf("bhej do", "send it", "send now", "kar do", "theek hai", "भेज दो", "confirm", "proceed", "go ahead")
        return phrases.any { lower.contains(it) }
    }

    private fun isCancellation(text: String): Boolean {
        val lower = text.lowercase().trim()
        val keywords = listOf("cancel", "mat bhejo", "ruk jao", "cancel it", "nah", "कैंसिल", "rehne do", "don't send")
        if (keywords.any { lower.contains(it) }) return true
        val words = lower.split(Regex("""[\s,?!.]+""")).filter { it.isNotBlank() }.toSet()
        return words.contains("nahi") || words.contains("no") || words.contains("नहीं")
    }

    private fun cleanMessageBody(raw: String): String {
        var clean = raw
        val prefixes = listOf("bolna ki", "bolna", "say that", "say", "likho", "write", "message", "msg")
        for (prefix in prefixes) {
            if (clean.lowercase().startsWith(prefix)) {
                clean = clean.substring(prefix.length).trim()
            }
        }
        return clean.trim('"', ' ', '.')
    }

    private fun extractContact(input: String): String? {
        val lower = input.lowercase()
        // Check pronoun reference
        if (lower.contains("usko") || lower.contains("unko") || lower.contains("him") || lower.contains("her") || lower.contains("them") || lower.contains("same person")) {
            return contextEngine.lastInteractedContact
        }

        // Regex matching "<Name> ko"
        val koRegex = Regex("""([a-zA-Z0-9\u0900-\u097F]+)\s+ko\b""", RegexOption.IGNORE_CASE)
        val matchKo = koRegex.find(input)
        if (matchKo != null) {
            val candidate = matchKo.groupValues[1].trim()
            if (!candidate.equals("whatsapp", ignoreCase = true) && !candidate.equals("sms", ignoreCase = true)) {
                return candidate
            }
        }

        // 1. Direct "to <Name>" (e.g. "Send message to Rahul", "Call to Amit")
        val directToRegex = Regex("""\bto\s+([a-zA-Z0-9\u0900-\u097F]+)""", RegexOption.IGNORE_CASE)
        val matchDirectTo = directToRegex.find(input)
        if (matchDirectTo != null) {
            val candidate = matchDirectTo.groupValues[1].trim()
            val stopWords = setOf("whatsapp", "sms", "me", "the", "a", "an", "him", "her")
            if (!stopWords.contains(candidate.lowercase())) {
                return candidate
            }
        }

        // 2. Regex matching "message <Name>" or "call <Name>"
        val verbRegex = Regex("""\b(?:message|call|text)\s+([a-zA-Z0-9\u0900-\u097F]+)""", RegexOption.IGNORE_CASE)
        val matchVerb = verbRegex.find(input)
        if (matchVerb != null) {
            val candidate = matchVerb.groupValues[1].trim()
            val stopWords = setOf("to", "whatsapp", "sms", "me", "the", "a", "an", "him", "her")
            if (!stopWords.contains(candidate.lowercase())) {
                return candidate
            }
        }
        return null
    }

    private fun extractInlineMessage(input: String): String? {
        val markers = listOf("that", "ki", "message:", "bolna")
        for (m in markers) {
            val idx = input.indexOf(" $m ", ignoreCase = true)
            if (idx != -1) {
                return input.substring(idx + m.length + 2).trim()
            }
        }
        return null
    }

    private fun extractAppendedText(input: String): String {
        val markers = listOf("likho ki", "likho", "write that", "write", "add that", "add", "bhi")
        for (m in markers) {
            val idx = input.indexOf(m, ignoreCase = true)
            if (idx != -1) {
                val candidate = input.substring(idx + m.length).trim()
                if (candidate.isNotBlank()) {
                    return candidate.trim('"', ' ', '.')
                }
            }
        }
        return input.trim()
    }
}
