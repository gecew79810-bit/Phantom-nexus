package com.pantham.nexus.learning.engine

import com.pantham.nexus.learning.model.*

class NexusOutcomeInferenceEngine {

    fun inferFromUserResponse(
        text: String
    ): OutcomeStatus? {

        val q = text.lowercase()

        return when {
            q.contains("worked") ||
            q.contains("success") ||
            q.contains("sahi hua") ||
            q.contains("ho gaya") ||
            q.contains("अच्छा हुआ") ||
            q.contains("हो गया") ->
                OutcomeStatus.SUCCESS

            q.contains("failed") ||
            q.contains("fail") ||
            q.contains("nahi hua") ||
            q.contains("काम नहीं किया") ->
                OutcomeStatus.FAILURE

            q.contains("partially") ||
            q.contains("thoda") ||
            q.contains("aadha") ||
            q.contains("कुछ हद तक") ->
                OutcomeStatus.PARTIAL

            q.contains("cancel") ||
            q.contains("chhod do") ||
            q.contains("रहने दो") ->
                OutcomeStatus.CANCELLED

            else ->
                null
        }
    }
}
