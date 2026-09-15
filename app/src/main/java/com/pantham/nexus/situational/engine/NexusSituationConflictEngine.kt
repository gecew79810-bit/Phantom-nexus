package com.pantham.nexus.situational.engine

import com.pantham.nexus.situational.model.*

class NexusSituationConflictEngine {

    fun detect(
        device: DeviceSituation,
        app: AppSituation,
        network: NetworkSituation,
        session: SessionSituation,
        goal: GoalSituation,
        action: ActionSituation,
        temporal: TemporalSituation
    ): List<SituationConflict> {

        val conflicts = mutableListOf<SituationConflict>()

        if (
            goal.active &&
            goal.blocked &&
            action.pending &&
            action.requiresConfirmation
        ) {
            conflicts += SituationConflict(
                leftSignal = "Active goal is blocked",
                rightSignal = "Pending confirmation required",
                severity = 0.8f,
                explanation = "Goal progress is waiting on an action confirmation."
            )
        }

        if (
            session.active &&
            !device.screenOn
        ) {
            conflicts += SituationConflict(
                leftSignal = "Session active",
                rightSignal = "Screen off",
                severity = 0.45f,
                explanation = "Voice/session state may continue while screen is off."
            )
        }

        if (
            action.pending &&
            !network.connected
        ) {
            conflicts += SituationConflict(
                leftSignal = "Action pending",
                rightSignal = "Network unavailable",
                severity = 0.65f,
                explanation = "Pending action may be blocked by connectivity."
            )
        }

        if (
            temporal.timeSensitive &&
            !goal.active &&
            !action.pending
        ) {
            conflicts += SituationConflict(
                leftSignal = "Time-sensitive event",
                rightSignal = "No active task",
                severity = 0.3f,
                explanation = "A time-sensitive event exists without a corresponding active goal."
            )
        }

        return conflicts
    }
}
