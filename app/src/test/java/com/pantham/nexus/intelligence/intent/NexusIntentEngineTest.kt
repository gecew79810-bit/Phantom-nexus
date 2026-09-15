package com.pantham.nexus.intelligence.intent

import com.pantham.nexus.intelligence.model.ConfidenceLevel
import com.pantham.nexus.intelligence.model.IntentType
import com.pantham.nexus.intelligence.model.RiskLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusIntentEngineTest {

    @Test
    fun testDeterministicFallbackPatterns() = runBlocking {
        val dummyModel = object : SemanticIntentModel {
            override suspend fun classify(text: String): List<SemanticIntentCandidate> = emptyList()
        }

        val engine = NexusIntentEngine(semanticModel = dummyModel)

        // Test OPEN_APP
        val openAppResult = engine.detect("open Spotify")
        assertEquals(IntentType.OPEN_APP, openAppResult.type)
        assertEquals(ConfidenceLevel.HIGH, openAppResult.confidenceLevel)
        assertEquals(RiskLevel.LOW, openAppResult.risk)

        // Test SEND_MESSAGE with missing slots
        val msgResult = engine.detect("send message to Rahul")
        assertEquals(IntentType.SEND_MESSAGE, msgResult.type)
        assertEquals(RiskLevel.HIGH, msgResult.risk)
        assertTrue(msgResult.missingSlots.contains("message"))

        // Test Hindi pattern
        val callResult = engine.detect("Rahul ko call karo")
        assertEquals(IntentType.MAKE_CALL, callResult.type)
        assertEquals(RiskLevel.HIGH, callResult.risk)
    }

    @Test
    fun testSemanticModelCandidatePriority() = runBlocking {
        val customModel = object : SemanticIntentModel {
            override suspend fun classify(text: String): List<SemanticIntentCandidate> {
                return listOf(
                    SemanticIntentCandidate(IntentType.RESEARCH, 0.95)
                )
            }
        }

        val engine = NexusIntentEngine(semanticModel = customModel)
        val result = engine.detect("compare flagship processors")
        assertEquals(IntentType.RESEARCH, result.type)
        assertEquals(ConfidenceLevel.VERY_HIGH, result.confidenceLevel)
        assertEquals(RiskLevel.LOW, result.risk)
    }
}
