package com.pantham.nexus.situational.security

import com.pantham.nexus.situational.model.*

class NexusSituationPrivacyGate {

    fun sanitize(
        context: SituationalContext
    ): SituationalContext {

        // Never retain raw notification text,
        // raw voice transcripts,
        // raw screen contents,
        // raw OCR text,
        // credentials,
        // tokens,
        // passwords,
        // private message bodies
        // at this correlation layer.

        val sanitizedNotifications =
            context.notifications.copy(
                latestPackage = context.notifications.latestPackage
            )

        val sanitizedVision =
            context.vision.copy(
                summary = context.vision.summary?.take(300)
            )

        return context.copy(
            notifications = sanitizedNotifications,
            vision = sanitizedVision
        )
    }
}
