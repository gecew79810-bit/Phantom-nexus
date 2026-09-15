package com.pantham.nexus.decision.parser

import com.pantham.nexus.decision.model.*

class NexusDecisionQueryParser {

    fun detectIntent(query: String): DecisionIntent {

        val q = query.lowercase()

        return when {
            q.contains("which") ||
            q.contains("better") ||
            q.contains("best") ||
            q.contains("kaunsa") ||
            q.contains("kaun sa") ||
            q.contains("कौन") ||
            q.contains("बेहतर") ->
                DecisionIntent.CHOOSE

            q.contains("compare") ||
            q.contains("comparison") ||
            q.contains("compare karo") ||
            q.contains("तुलना") ->
                DecisionIntent.COMPARE

            q.contains("recommend") ||
            q.contains("suggest") ||
            q.contains("recommendation") ||
            q.contains("सलाह") ->
                DecisionIntent.RECOMMEND

            q.contains("rank") ||
            q.contains("ranking") ||
            q.contains("क्रम") ->
                DecisionIntent.RANK

            q.contains("pros") ||
            q.contains("cons") ||
            q.contains("advantage") ||
            q.contains("disadvantage") ||
            q.contains("फायदा") ||
            q.contains("नुकसान") ->
                DecisionIntent.EVALUATE

            q.contains("tradeoff") ||
            q.contains("trade-off") ||
            q.contains("compromise") ->
                DecisionIntent.TRADEOFF

            else ->
                DecisionIntent.UNKNOWN
        }
    }


    fun extractOptionNames(
        query: String
    ): List<String> {

        val q = query.trim()

        val separators =
            Regex(
                """\s*(?:,\s*|\s+(?:vs\.?|versus|or|ya|या|aur|और|and)\s+)""",
                RegexOption.IGNORE_CASE
            )

        val pieces =
            q.split(separators)
                .map { it.trim() }
                .filter { it.length >= 2 }

        return pieces.take(10)
    }
}
