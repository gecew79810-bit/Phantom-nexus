package com.pantham.nexus.prediction

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusPredictionTests {

    @Test
    fun repeatedIntentCreatesPrediction() = runBlocking {
        val repo = InMemoryPredictionRepository()
        val engine = NexusPredictionEngine(repo)

        val result = engine.predict(
            PredictionRequest(
                context = PredictionContext(
                    userText = "continue",
                    currentIntent = "SEND_MESSAGE",
                    recentIntents = listOf("SEND_MESSAGE", "SEND_MESSAGE")
                )
            )
        )

        assertTrue(result.predictions.any { it.type == PredictionType.FOLLOW_UP })
    }

    @Test
    fun missingSlotDetected() = runBlocking {
        val predictor = NexusMissingInformationPredictor(NexusPredictionConfidenceEngine())

        val prediction = predictor.predict(
            ActionRequirement(
                action = "SEND_MESSAGE",
                requiredSlots = setOf("recipient", "message"),
                availableSlots = mapOf("recipient" to "Rahul")
            )
        )

        assertNotNull(prediction)
        assertTrue("message" in (prediction?.missingSlots ?: emptySet()))
    }

    @Test
    fun noMissingSlotReturnsNull() {
        val predictor = NexusMissingInformationPredictor(NexusPredictionConfidenceEngine())

        val result = predictor.predict(
            ActionRequirement(
                action = "SEND_MESSAGE",
                requiredSlots = setOf("recipient"),
                availableSlots = mapOf("recipient" to "Rahul")
            )
        )

        assertNull(result)
    }

    @Test
    fun routinePatternDetected() {
        val engine = NexusBehaviorPatternEngine()
        val events = (1..5).map {
            BehaviorEvent(action = "OPEN_MUSIC", timestamp = it * 1000L)
        }

        val patterns = engine.detect(events)
        assertTrue(patterns.isNotEmpty())
        assertEquals("OPEN_MUSIC", patterns.first().action)
    }

    @Test
    fun weakRoutineSuppression() {
        val engine = NexusRoutinePredictor(NexusPredictionConfidenceEngine())
        val weakPatterns = listOf(
            BehaviorPattern(
                action = "ONE_OFF_ACTION",
                occurrences = 1,
                successRate = 1.0f,
                strength = 0.2f
            )
        )

        val predictions = engine.predict(weakPatterns)
        assertTrue(predictions.isEmpty())
    }

    @Test
    fun destructiveActionCreatesRisk() {
        val predictor = NexusRiskPredictor()
        val prediction = predictor.predict(
            PlannedActionRiskInput(
                action = "DELETE_FILE",
                destructive = true
            )
        )

        assertNotNull(prediction)
        assertTrue((prediction?.riskLevel ?: RiskLevel.NONE) >= RiskLevel.HIGH)
    }

    @Test
    fun irreversibleActionRequiresConfirmation() {
        val predictor = NexusRiskPredictor()
        val policy = NexusPredictionPolicyEngine()

        val prediction = predictor.predict(
            PlannedActionRiskInput(
                action = "IRREVERSIBLE_ACTION",
                irreversible = true
            )
        )

        assertNotNull(prediction)
        val decision = policy.evaluate(prediction!!)
        assertEquals(PredictionDisposition.REQUIRE_CONFIRMATION, decision.disposition)
    }

    @Test
    fun upcomingEventCreatesPreparationPrediction() {
        val predictor = NexusPreparationPredictor(NexusPredictionConfidenceEngine())
        val now = 1_000_000L

        val result = predictor.predict(
            UpcomingContextEvent(
                title = "Project Meeting",
                startTime = now + 2L * 60L * 60L * 1000L
            ),
            now
        )

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun distantEventDoesNotCreateSuggestion() {
        val predictor = NexusPreparationPredictor(NexusPredictionConfidenceEngine())
        val now = 1_000_000L

        val result = predictor.predict(
            UpcomingContextEvent(
                title = "Future Event",
                startTime = now + 72L * 60L * 60L * 1000L
            ),
            now
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun lowConfidencePredictionIgnored() {
        val policy = NexusPredictionPolicyEngine()
        val prediction = NexusPrediction(
            type = PredictionType.PROACTIVE_SUGGESTION,
            title = "Test",
            description = "Test",
            confidence = PredictionConfidence.VERY_LOW,
            confidenceScore = 0.10f,
            disposition = PredictionDisposition.SUGGEST
        )

        val decision = policy.evaluate(prediction)
        assertEquals(PredictionDisposition.IGNORE, decision.disposition)
    }

    @Test
    fun feedbackStored() = runBlocking {
        val repo = InMemoryPredictionRepository()
        repo.saveFeedback(
            PredictionFeedbackRecord(
                predictionId = "123",
                feedback = PredictionFeedback.ACCEPTED
            )
        )

        assertEquals(1, repo.getFeedback("123").size)
    }

    @Test
    fun acceptanceFeedbackCalculation() = runBlocking {
        val repo = InMemoryPredictionRepository()
        val engine = NexusPredictionFeedbackEngine(repo)

        engine.record("p1", PredictionFeedback.ACCEPTED)
        engine.record("p2", PredictionFeedback.REJECTED)

        val rate = engine.acceptanceRate()
        assertEquals(0.5f, rate, 0.01f)
    }

    @Test
    fun failureGuardDoesNotCrash() = runBlocking {
        val repository = InMemoryPredictionRepository()
        val controller = NexusPredictionController(repository)
        val guard = NexusPredictionFailureGuard(controller)

        val result = guard.safeProcess(
            PredictionRequest(
                PredictionContext(userText = "")
            )
        )

        assertNotNull(result)
        assertTrue(result.predictions.isEmpty())
    }

    @Test
    fun safePredictionDoesNotBecomeActionAutomatically() {
        val policy = NexusPredictionPolicyEngine()
        val bridge = NexusPredictiveActionBridge(policy)

        val prediction = NexusPrediction(
            type = PredictionType.NEXT_INTENT,
            title = "Possible next intent",
            description = "Internal prediction",
            predictedAction = "SEND_MESSAGE",
            confidence = PredictionConfidence.HIGH,
            confidenceScore = 0.8f,
            disposition = PredictionDisposition.INTERNAL_ONLY
        )

        val candidate = bridge.convert(prediction)
        assertNull(candidate)
    }

    @Test
    fun suggestionCooldownSuppression() = runBlocking {
        val repo = InMemoryPredictionRepository()
        val throttle = NexusSuggestionThrottle(repo)

        val prediction = NexusPrediction(
            type = PredictionType.PROACTIVE_SUGGESTION,
            title = "Drink water",
            description = "Hydration reminder",
            predictedAction = "HYDRATE",
            confidence = PredictionConfidence.HIGH,
            confidenceScore = 0.85f,
            disposition = PredictionDisposition.SUGGEST
        )

        // First time should allow
        assertTrue(throttle.shouldShow(prediction))

        // Save into repo as recent
        repo.savePrediction(prediction)

        // Within cooldown should suppress
        assertFalse(throttle.shouldShow(prediction))
    }

    @Test
    fun personalKnowledgeContextIntegration() = runBlocking {
        val repo = InMemoryPredictionRepository()
        val controller = NexusPredictionController(repo)
        val bridge = StandardPredictionContextBridge(controller)

        val nexusContext = com.pantham.nexus.intelligence.model.NexusContext(
            userInput = "Tell me about Rahul's project",
            knowledgeContext = com.pantham.nexus.knowledge.KnowledgeContext(
                relevantEntities = listOf(
                    com.pantham.nexus.knowledge.KnowledgeEntity(
                        canonicalName = "Rahul",
                        type = com.pantham.nexus.knowledge.KnowledgeEntityType.PERSON,
                        source = com.pantham.nexus.knowledge.KnowledgeSource.USER_EXPLICIT
                    )
                ),
                relevantRelationships = emptyList(),
                relevantObservations = emptyList()
            )
        )

        val req = bridge.buildPredictionContext(nexusContext)
        assertTrue(req.context.knowledgeSignals.contains("Rahul"))
    }
}
