package com.pantham.nexus.situational.engine

import com.pantham.nexus.situational.model.*

class NexusSituationPriorityEngine {

    fun calculate(
        device: DeviceSituation,
        app: AppSituation,
        network: NetworkSituation,
        session: SessionSituation,
        notifications: NotificationSituation,
        goal: GoalSituation,
        action: ActionSituation,
        prediction: PredictionSituation,
        temporal: TemporalSituation,
        conflicts: List<SituationConflict>
    ): SituationPriority {

        var score = 0f

        if (notifications.urgentCount > 0) {
            score += 0.30f
        }

        if (action.pending) {
            score += 0.25f
        }

        if (goal.active) {
            score += 0.20f
        }

        if (goal.blocked) {
            score += 0.15f
        }

        if (temporal.timeSensitive) {
            score += 0.25f
        }

        if (session.active) {
            score += 0.10f
        }

        if (!network.connected && action.pending) {
            score += 0.20f
        }

        if (device.batteryLow) {
            score += 0.08f
        }

        score += conflicts.maxOfOrNull { it.severity } ?: 0f

        return when {
            score >= 1.0f -> SituationPriority.CRITICAL
            score >= 0.70f -> SituationPriority.HIGH
            score >= 0.40f -> SituationPriority.MEDIUM
            score >= 0.15f -> SituationPriority.LOW
            else -> SituationPriority.BACKGROUND
        }
    }
}
