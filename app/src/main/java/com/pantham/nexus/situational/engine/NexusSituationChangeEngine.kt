package com.pantham.nexus.situational.engine

import com.pantham.nexus.situational.model.*

class NexusSituationChangeEngine {

    fun detect(
        previous: SituationalContext?,
        current: SituationalContext
    ): SituationChangeEvent {

        if (previous == null) {
            return SituationChangeEvent(
                previous = null,
                current = current,
                type = SituationChangeType.MAJOR_CONTEXT_CHANGE,
                significance = 1f,
                reason = "Initial situational context."
            )
        }

        if (previous.app.packageName != current.app.packageName) {
            return event(
                previous,
                current,
                SituationChangeType.APP_CHANGED,
                0.72f,
                "Foreground application changed."
            )
        }

        if (previous.network.connected != current.network.connected ||
            previous.network.transport != current.network.transport
        ) {
            return event(
                previous,
                current,
                SituationChangeType.NETWORK_CHANGED,
                0.70f,
                "Network state changed."
            )
        }

        if (previous.session.active != current.session.active ||
            previous.session.listening != current.session.listening ||
            previous.session.speaking != current.session.speaking
        ) {
            return event(
                previous,
                current,
                SituationChangeType.SESSION_CHANGED,
                0.80f,
                "Assistant session state changed."
            )
        }

        if (previous.goal.goalId != current.goal.goalId ||
            previous.goal.blocked != current.goal.blocked
        ) {
            return event(
                previous,
                current,
                SituationChangeType.GOAL_CHANGED,
                0.86f,
                "Active goal state changed."
            )
        }

        if (previous.action.actionId != current.action.actionId ||
            previous.action.pending != current.action.pending
        ) {
            return event(
                previous,
                current,
                SituationChangeType.ACTION_CHANGED,
                0.90f,
                "Action state changed."
            )
        }

        if (previous.activity.mode != current.activity.mode) {
            return event(
                previous,
                current,
                SituationChangeType.ACTIVITY_CHANGED,
                0.62f,
                "Inferred activity changed."
            )
        }

        if (previous.temporal.upcomingEvent != current.temporal.upcomingEvent ||
            previous.temporal.timeSensitive != current.temporal.timeSensitive
        ) {
            return event(
                previous,
                current,
                SituationChangeType.TEMPORAL_CHANGED,
                0.55f,
                "Temporal context changed."
            )
        }

        return SituationChangeEvent(
            previous = previous,
            current = current,
            type = SituationChangeType.NONE,
            significance = 0f,
            reason = "No meaningful situation change detected."
        )
    }

    private fun event(
        previous: SituationalContext,
        current: SituationalContext,
        type: SituationChangeType,
        significance: Float,
        reason: String
    ) = SituationChangeEvent(
        previous = previous,
        current = current,
        type = type,
        significance = significance,
        reason = reason
    )
}
