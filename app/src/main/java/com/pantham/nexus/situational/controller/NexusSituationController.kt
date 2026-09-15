package com.pantham.nexus.situational.controller

import com.pantham.nexus.situational.engine.NexusSituationalAwarenessEngine
import com.pantham.nexus.situational.model.SituationalContext
import com.pantham.nexus.situational.security.NexusSituationPrivacyGate

class NexusSituationController(
    private val engine: NexusSituationalAwarenessEngine,
    private val privacyGate: NexusSituationPrivacyGate =
        NexusSituationPrivacyGate()
) {

    suspend fun refresh(): SituationalContext {
        val raw = engine.buildContext()
        return privacyGate.sanitize(raw)
    }

    fun current(): SituationalContext? {
        return engine.currentContext()?.let {
            privacyGate.sanitize(it)
        }
    }
}
