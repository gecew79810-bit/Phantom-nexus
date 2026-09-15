package com.pantham.nexus.situational.engine

import com.pantham.nexus.situational.model.*

class NexusSituationInterruptionEngine {

    fun evaluate(
        session: SessionSituation,
        notifications: NotificationSituation,
        temporal: TemporalSituation,
        action: ActionSituation,
        conflicts: List<SituationConflict>
    ): InterruptionLevel {

        var score = 0f

        if (notifications.urgentCount > 0) {
            score += 0.55f
        }

        if (temporal.timeSensitive) {
            score += 0.35f
        }

        if (action.requiresConfirmation) {
            score += 0.25f
        }

        if (conflicts.any { it.severity >= 0.85f }) {
            score += 0.35f
        }

        if (!session.interruptionAllowed) {
            score -= 0.25f
        }

        return when {
            score >= 0.90f -> InterruptionLevel.CRITICAL
            score >= 0.65f -> InterruptionLevel.HIGH
            score >= 0.35f -> InterruptionLevel.MEDIUM
            score > 0f -> InterruptionLevel.LOW
            else -> InterruptionLevel.NONE
        }
    }
}
