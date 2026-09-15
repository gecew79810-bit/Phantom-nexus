package com.pantham.nexus.situational.integration

import com.pantham.nexus.situational.engine.NexusSituationalAwarenessEngine
import com.pantham.nexus.situational.model.SituationalContext

class NexusSituationContextAdapter(
    private val engine: NexusSituationalAwarenessEngine
) {

    suspend fun getSituationalContext(): SituationalContext {
        return engine.buildContext()
    }

    fun current(): SituationalContext? {
        return engine.currentContext()
    }
}
