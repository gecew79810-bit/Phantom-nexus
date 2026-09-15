package com.pantham.nexus.situational.engine

import com.pantham.nexus.situational.model.*

class NexusSituationSummaryEngine {

    fun summarize(context: SituationalContext): String {

        val parts = mutableListOf<String>()

        context.app.appName?.let {
            parts += "Current app: $it"
        }

        if (context.activity.mode != ActivityMode.UNKNOWN) {
            parts += "Activity: ${context.activity.mode.name.lowercase()}"
        }

        if (context.goal.active) {
            parts += "Goal: ${context.goal.title ?: "active goal"}"
        }

        if (context.action.pending) {
            parts += "Pending action: ${context.action.description ?: "action"}"
        }

        if (context.network.connected) {
            parts += "Network: connected"
        } else {
            parts += "Network: offline"
        }

        if (context.notifications.unreadCount > 0) {
            parts += "Notifications: ${context.notifications.unreadCount}"
        }

        context.temporal.upcomingEvent?.let {
            parts += "Upcoming: $it"
        }

        context.focus?.let {
            parts += "Focus: ${it.title}"
        }

        if (parts.isEmpty()) {
            return "No strong situational signals available."
        }

        return parts.joinToString(" • ")
    }
}
