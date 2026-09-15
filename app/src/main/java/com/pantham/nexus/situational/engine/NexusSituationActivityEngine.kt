package com.pantham.nexus.situational.engine

import com.pantham.nexus.situational.model.*

class NexusSituationActivityEngine {

    fun infer(
        app: AppSituation,
        session: SessionSituation,
        vision: VisionSituation,
        goal: GoalSituation
    ): ActivitySituation {

        if (session.speaking) {
            return ActivitySituation(
                mode = ActivityMode.SPEAKING,
                confidence = 0.95f,
                inferredFrom = setOf(SituationSource.SESSION)
            )
        }

        if (session.listening) {
            return ActivitySituation(
                mode = ActivityMode.LISTENING,
                confidence = 0.95f,
                inferredFrom = setOf(SituationSource.SESSION)
            )
        }

        if (goal.active) {
            return ActivitySituation(
                mode = ActivityMode.WORKING,
                confidence = 0.72f,
                inferredFrom = setOf(SituationSource.GOAL)
            )
        }

        val category = app.category?.lowercase().orEmpty()

        return when {
            category.contains("video") ||
                category.contains("stream") -> {

                ActivitySituation(
                    mode = ActivityMode.WATCHING,
                    confidence = 0.84f,
                    inferredFrom = setOf(SituationSource.APP)
                )
            }

            category.contains("navigation") ||
                category.contains("maps") -> {

                ActivitySituation(
                    mode = ActivityMode.NAVIGATING,
                    confidence = 0.90f,
                    inferredFrom = setOf(SituationSource.APP)
                )
            }

            category.contains("shopping") -> {

                ActivitySituation(
                    mode = ActivityMode.SHOPPING,
                    confidence = 0.82f,
                    inferredFrom = setOf(SituationSource.APP)
                )
            }

            category.contains("communication") ||
                category.contains("messaging") -> {

                ActivitySituation(
                    mode = ActivityMode.TYPING,
                    confidence = 0.70f,
                    inferredFrom = setOf(SituationSource.APP)
                )
            }

            vision.available && vision.textDetected -> {

                ActivitySituation(
                    mode = ActivityMode.READING,
                    confidence = 0.60f,
                    inferredFrom = setOf(SituationSource.VISION)
                )
            }

            app.foreground -> {

                ActivitySituation(
                    mode = ActivityMode.WORKING,
                    confidence = 0.40f,
                    inferredFrom = setOf(SituationSource.APP)
                )
            }

            else -> ActivitySituation(
                mode = ActivityMode.UNKNOWN,
                confidence = 0.1f
            )
        }
    }
}
