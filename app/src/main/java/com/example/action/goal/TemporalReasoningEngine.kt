package com.example.action.goal

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class NormalizedTemporalResult(
    val originalExpression: String,
    val targetTimestampMs: Long,
    val formattedTargetTime: String,
    val isRelative: Boolean,
    val confidence: Float = 0.95f
)

class TemporalReasoningEngine(private val timeZone: TimeZone = TimeZone.getDefault()) {

    fun parseTemporalExpression(input: String, baseTimeMs: Long = System.currentTimeMillis()): NormalizedTemporalResult? {
        val lower = input.lowercase().trim()
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = baseTimeMs }

        var isRelative = false
        var matched = false

        when {
            // Minutes relative: "in 20 minutes", "20 minute baad", "20 min"
            Regex("""(?:in\s+)?(\d+)\s*(?:minutes?|mins?|मिनट)(?:\s+baad|\s+later)?""").find(lower) != null -> {
                val match = Regex("""(?:in\s+)?(\d+)\s*(?:minutes?|mins?|मिनट)""").find(lower)!!
                val mins = match.groupValues[1].toIntOrNull() ?: 10
                cal.add(Calendar.MINUTE, mins)
                isRelative = true
                matched = true
            }

            // Hours relative: "in two hours", "in 2 hours", "2 ghante baad"
            Regex("""(?:in\s+)?(\d+|one|two|three|four)\s*(?:hours?|hrs?|घंटे|घंटा)""").find(lower) != null -> {
                val match = Regex("""(?:in\s+)?(\d+|one|two|three|four)\s*(?:hours?|hrs?|घंटे|घंटा)""").find(lower)!!
                val numStr = match.groupValues[1]
                val hours = when (numStr) {
                    "one" -> 1
                    "two" -> 2
                    "three" -> 3
                    "four" -> 4
                    else -> numStr.toIntOrNull() ?: 1
                }
                cal.add(Calendar.HOUR_OF_DAY, hours)
                isRelative = true
                matched = true
            }

            // Half hour relative / Aadhe ghante baad: "aadhe ghante baad", "aadha ghanta baad", "in half an hour", "half hour later"
            lower.contains("aadhe ghante baad") || lower.contains("aadha ghanta baad") || lower.contains("आधे घंटे बाद") || lower.contains("आधा घंटा बाद") || lower.contains("half an hour") || lower.contains("half hour") -> {
                cal.add(Calendar.MINUTE, 30)
                isRelative = true
                matched = true
            }

            // Specific relative offset: "aadha ghanta pehle", "half an hour before", "30 minute pehle"
            lower.contains("aadha ghanta pehle") || lower.contains("आधा घंटा पहले") || lower.contains("half an hour before") || lower.contains("30 minute pehle") -> {
                // If referenced to tomorrow or meeting, advance to meeting time and subtract 30 mins
                if (lower.contains("kal") || lower.contains("tomorrow")) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                // Default meeting base is 10:00 AM unless specified
                cal.set(Calendar.HOUR_OF_DAY, 9)
                cal.set(Calendar.MINUTE, 30)
                cal.set(Calendar.SECOND, 0)
                isRelative = true
                matched = true
            }

            // Next Week / Agle Hafte: "next week", "agle hafte", "अगले हफ्ते"
            lower.contains("next week") || lower.contains("agle hafte") || lower.contains("अगले हफ्ते") -> {
                cal.add(Calendar.WEEK_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 10)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                isRelative = true
                matched = true
            }

            // Tomorrow: "tomorrow", "kal", "कल"
            lower.contains("tomorrow") || lower.contains("kal") || lower.contains("कल") -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                // Check if specific hour mentioned e.g. "9 baje", "at 9", "subah 9"
                val hourMatch = Regex("""(\d{1,2})\s*(?:baje|बजे|am|pm|\s*:\s*\d{2})""").find(lower)
                    ?: Regex("""at\s+(\d{1,2})""").find(lower)
                if (hourMatch != null) {
                    val rawH = hourMatch.groupValues[1].toIntOrNull() ?: 9
                    val h = if ((lower.contains("shaam") || lower.contains("raat") || lower.contains("pm")) && rawH < 12) rawH + 12 else rawH
                    cal.set(Calendar.HOUR_OF_DAY, h)
                    cal.set(Calendar.MINUTE, 0)
                } else if (lower.contains("evening") || lower.contains("shaam") || lower.contains("शाम")) {
                    cal.set(Calendar.HOUR_OF_DAY, 18)
                    cal.set(Calendar.MINUTE, 0)
                } else if (lower.contains("afternoon") || lower.contains("dopahar") || lower.contains("दोपहर")) {
                    cal.set(Calendar.HOUR_OF_DAY, 14)
                    cal.set(Calendar.MINUTE, 0)
                } else {
                    cal.set(Calendar.HOUR_OF_DAY, 9)
                    cal.set(Calendar.MINUTE, 0)
                }
                cal.set(Calendar.SECOND, 0)
                matched = true
            }

            // Tonight / This evening: "tonight", "this evening", "aaj shaam", "आज शाम", "aaj raat"
            lower.contains("tonight") || lower.contains("this evening") || lower.contains("aaj shaam") || lower.contains("आज शाम") || lower.contains("aaj raat") -> {
                val hourMatch = Regex("""(?:at\s+|at\s*|@\s*)(\d{1,2})""").find(lower)
                    ?: Regex("""(\d{1,2})\s*(?:baje|बजे|pm)""").find(lower)
                val rawH = hourMatch?.groupValues?.get(1)?.toIntOrNull() ?: 7
                val h = if (rawH < 12) rawH + 12 else rawH
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                matched = true
            }

            // After lunch: "after lunch", "lunch ke baad", "लंच के बाद"
            lower.contains("after lunch") || lower.contains("lunch ke baad") || lower.contains("लंच के बाद") -> {
                cal.set(Calendar.HOUR_OF_DAY, 14)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                matched = true
            }

            // Next Monday: "next monday", "agle somwar", "अगले सोमवार"
            lower.contains("next monday") || lower.contains("agle somwar") || lower.contains("सोमवार") -> {
                cal.add(Calendar.WEEK_OF_YEAR, 1)
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                cal.set(Calendar.HOUR_OF_DAY, 10)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                matched = true
            }

            // Today: "today", "aaj", "आज"
            lower.contains("today") || lower.contains("aaj") || lower.contains("आज") -> {
                // Keep today's date, advance by 1 hour if unspecified
                cal.add(Calendar.HOUR_OF_DAY, 1)
                matched = true
            }
        }

        if (!matched) return null

        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply {
            this.timeZone = this@TemporalReasoningEngine.timeZone
        }

        return NormalizedTemporalResult(
            originalExpression = input,
            targetTimestampMs = cal.timeInMillis,
            formattedTargetTime = formatter.format(Date(cal.timeInMillis)),
            isRelative = isRelative
        )
    }
}
