package com.pantham.nexus.intelligence.intent

import com.pantham.nexus.intelligence.model.ConfidenceLevel
import com.pantham.nexus.intelligence.model.IntentResult
import com.pantham.nexus.intelligence.model.IntentType
import com.pantham.nexus.intelligence.model.RiskLevel

data class SemanticIntentCandidate(
    val intent: IntentType,
    val confidence: Double
)

interface SemanticIntentModel {

    suspend fun classify(
        text: String
    ): List<SemanticIntentCandidate>
}

class NexusIntentEngine(
    private val semanticModel: SemanticIntentModel,
    private val deterministicPatterns: List<IntentPattern> =
        IntentPattern.defaults()
) {

    suspend fun detect(
        input: String
    ): IntentResult {

        val normalized =
            normalize(input)

        val modelCandidates =
            runCatching {
                semanticModel.classify(
                    normalized
                )
            }.getOrDefault(
                emptyList()
            )

        val deterministic =
            deterministicPatterns
                .mapNotNull { pattern ->

                    val score =
                        pattern.matches(
                            normalized
                        )

                    if (score <= 0.0) {
                        null
                    } else {
                        SemanticIntentCandidate(
                            intent = pattern.intent,
                            confidence = score
                        )
                    }
                }

        val combined =
            (modelCandidates + deterministic)
                .groupBy {
                    it.intent
                }
                .map { (intent, values) ->

                    val best =
                        values.maxOfOrNull {
                            it.confidence
                        } ?: 0.0

                    SemanticIntentCandidate(
                        intent = intent,
                        confidence = best
                    )
                }
                .sortedByDescending {
                    it.confidence
                }

        val winner =
            combined.firstOrNull()
                ?: return IntentResult(
                    type = IntentType.UNKNOWN,
                    confidence = 0.0,
                    confidenceLevel = ConfidenceLevel.VERY_LOW
                )

        val missingSlots =
            missingSlotsFor(
                winner.intent,
                normalized
            )

        return IntentResult(
            type = winner.intent,
            confidence = winner.confidence.coerceIn(
                0.0,
                1.0
            ),
            confidenceLevel =
                confidenceLevel(
                    winner.confidence
                ),
            missingSlots = missingSlots,
            risk = riskFor(
                winner.intent
            )
        )
    }

    private fun missingSlotsFor(
        intent: IntentType,
        input: String
    ): List<String> {

        return when (intent) {

            IntentType.SEND_MESSAGE -> {

                buildList {

                    if (
                        !containsRecipientHint(input)
                    ) {
                        add("recipient")
                    }

                    if (
                        !containsMessageHint(input)
                    ) {
                        add("message")
                    }
                }
            }

            IntentType.MAKE_CALL -> {

                if (
                    !containsRecipientHint(input)
                ) {
                    listOf("recipient")
                } else {
                    emptyList()
                }
            }

            IntentType.CALENDAR -> {

                buildList {

                    if (
                        !containsEventHint(input)
                    ) {
                        add("event_title")
                    }

                    if (
                        !containsTimeHint(input)
                    ) {
                        add("start_time")
                    }
                }
            }

            IntentType.REMINDER -> {

                buildList {

                    if (
                        !containsReminderText(input)
                    ) {
                        add("reminder")
                    }

                    if (
                        !containsTimeHint(input)
                    ) {
                        add("trigger_time")
                    }
                }
            }

            else -> emptyList()
        }
    }

    private fun containsRecipientHint(
        input: String
    ): Boolean {

        return Regex(
            "(to|for|ko|ke liye)\\s+\\S+",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(
            input
        )
    }

    private fun containsMessageHint(
        input: String
    ): Boolean {

        return listOf(
            "saying",
            "that",
            "ki",
            "bol",
            "likh",
            "keh",
            "bata"
        ).any {
            input.contains(it)
        }
    }

    private fun containsEventHint(
        input: String
    ): Boolean {

        return listOf(
            "meeting",
            "appointment",
            "event",
            "call",
            "meeting"
        ).any {
            input.contains(it)
        }
    }

    private fun containsTimeHint(
        input: String
    ): Boolean {

        return Regex(
            "(today|tomorrow|kal|aaj|tonight|morning|evening|\\d{1,2}\\s*(am|pm)?)",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(
            input
        )
    }

    private fun containsReminderText(
        input: String
    ): Boolean {

        return listOf(
            "remind",
            "reminder",
            "yaad",
            "yaad dilana"
        ).any {
            input.contains(it)
        }
    }

    private fun confidenceLevel(
        value: Double
    ): ConfidenceLevel {

        return when {

            value >= 0.92 ->
                ConfidenceLevel.VERY_HIGH

            value >= 0.78 ->
                ConfidenceLevel.HIGH

            value >= 0.55 ->
                ConfidenceLevel.MEDIUM

            value >= 0.30 ->
                ConfidenceLevel.LOW

            else ->
                ConfidenceLevel.VERY_LOW
        }
    }

    private fun riskFor(
        intent: IntentType
    ): RiskLevel {

        return when (intent) {

            IntentType.SEND_MESSAGE,
            IntentType.MAKE_CALL ->
                RiskLevel.HIGH

            IntentType.FILE_OPERATION,
            IntentType.CALENDAR,
            IntentType.REMINDER,
            IntentType.AUTOMATION ->
                RiskLevel.MEDIUM

            else ->
                RiskLevel.LOW
        }
    }

    private fun normalize(
        value: String
    ): String {

        return value
            .lowercase()
            .replace(
                Regex("[^\\p{L}\\p{N}\\s]"),
                " "
            )
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }
}

interface IntentPattern {

    val intent: IntentType

    fun matches(
        normalizedInput: String
    ): Double

    companion object {

        fun defaults(): List<IntentPattern> =
            listOf(
                object : IntentPattern {

                    override val intent =
                        IntentType.OPEN_APP

                    override fun matches(
                        normalizedInput: String
                    ): Double {

                        return if (
                            normalizedInput.startsWith(
                                "open "
                            ) ||
                            normalizedInput.contains(
                                "khol"
                            ) ||
                            normalizedInput.contains(
                                "kholo"
                            )
                        ) 0.80 else 0.0
                    }
                },

                object : IntentPattern {

                    override val intent =
                        IntentType.SEND_MESSAGE

                    override fun matches(
                        normalizedInput: String
                    ): Double {

                        return if (
                            listOf(
                                "send message",
                                "message bhej",
                                "msg bhej",
                                "whatsapp pe",
                                "whatsapp par"
                            ).any {
                                normalizedInput.contains(it)
                            }
                        ) 0.87 else 0.0
                    }
                },

                object : IntentPattern {

                    override val intent =
                        IntentType.MAKE_CALL

                    override fun matches(
                        normalizedInput: String
                    ): Double {

                        return if (
                            normalizedInput.contains(
                                "call"
                            ) ||
                            normalizedInput.contains(
                                "phone karo"
                            ) ||
                            normalizedInput.contains(
                                "call karo"
                            )
                        ) 0.84 else 0.0
                    }
                },

                object : IntentPattern {

                    override val intent =
                        IntentType.MEDIA_CONTROL

                    override fun matches(
                        normalizedInput: String
                    ): Double {

                        return if (
                            listOf(
                                "play",
                                "pause",
                                "resume",
                                "next song",
                                "previous song",
                                "gana roko"
                            ).any {
                                normalizedInput.contains(it)
                            }
                        ) 0.82 else 0.0
                    }
                },

                object : IntentPattern {

                    override val intent =
                        IntentType.RESEARCH

                    override fun matches(
                        normalizedInput: String
                    ): Double {

                        return if (
                            listOf(
                                "research",
                                "compare",
                                "find best",
                                "deep research",
                                "research karo"
                            ).any {
                                normalizedInput.contains(it)
                            }
                        ) 0.89 else 0.0
                    }
                }
            )
    }
}
