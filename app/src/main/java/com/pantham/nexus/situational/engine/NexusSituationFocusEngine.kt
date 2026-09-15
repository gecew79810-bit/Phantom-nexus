package com.pantham.nexus.situational.engine

import com.pantham.nexus.situational.model.*

class NexusSituationFocusEngine {

    fun findFocus(
        notifications: NotificationSituation,
        goal: GoalSituation,
        action: ActionSituation,
        temporal: TemporalSituation,
        conflicts: List<SituationConflict>,
        app: AppSituation
    ): SituationFocus? {

        val candidates = mutableListOf<SituationFocusCandidate>()

        if (notifications.urgentCount > 0) {
            candidates += SituationFocusCandidate(
                title = "Urgent notification",
                reason = "${notifications.urgentCount} urgent notification(s) require attention.",
                score = 0.90f,
                sources = setOf(SituationSource.NOTIFICATION)
            )
        }

        if (action.pending) {
            candidates += SituationFocusCandidate(
                title = action.description ?: "Pending action",
                reason = "A pending action is waiting for completion.",
                score = if (action.requiresConfirmation) 0.95f else 0.80f,
                sources = setOf(SituationSource.ACTION)
            )
        }

        if (goal.blocked) {
            candidates += SituationFocusCandidate(
                title = goal.title ?: "Blocked goal",
                reason = "The active goal is blocked and requires progress.",
                score = 0.92f,
                sources = setOf(SituationSource.GOAL)
            )
        }

        if (temporal.timeSensitive) {
            candidates += SituationFocusCandidate(
                title = temporal.upcomingEvent ?: "Time-sensitive event",
                reason = "A time-sensitive event is approaching.",
                score = 0.88f,
                sources = setOf(SituationSource.TEMPORAL)
            )
        }

        if (conflicts.isNotEmpty()) {
            val severity = conflicts.maxOf { it.severity }

            candidates += SituationFocusCandidate(
                title = "Context conflict",
                reason = conflicts.maxBy { it.severity }.explanation,
                score = severity,
                sources = setOf(
                    SituationSource.SYSTEM,
                    SituationSource.ACTION,
                    SituationSource.GOAL
                )
            )
        }

        if (candidates.isEmpty() && app.foreground) {
            candidates += SituationFocusCandidate(
                title = app.appName ?: "Current application",
                reason = "Current foreground application is the strongest active context.",
                score = 0.35f,
                sources = setOf(SituationSource.APP)
            )
        }

        val best = candidates.maxByOrNull { it.score }
            ?: return null

        return SituationFocus(
            title = best.title,
            reason = best.reason,
            score = best.score,
            priority = when {
                best.score >= 0.90f -> SituationPriority.CRITICAL
                best.score >= 0.70f -> SituationPriority.HIGH
                best.score >= 0.40f -> SituationPriority.MEDIUM
                else -> SituationPriority.LOW
            },
            relatedSources = best.sources
        )
    }
}
