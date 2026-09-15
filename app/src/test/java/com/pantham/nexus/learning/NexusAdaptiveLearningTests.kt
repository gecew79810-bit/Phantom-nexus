package com.pantham.nexus.learning

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.dialogue.MultiTurnSessionManager
import com.example.ai.dialogue.MultiTurnState
import com.example.context.NexusContextEngine
import com.example.hardware.HardwareController
import com.pantham.nexus.decision.engine.NexusDecisionConstraintEngine
import com.pantham.nexus.decision.engine.NexusDecisionEngine
import com.pantham.nexus.decision.integration.DecisionExternalContext
import com.pantham.nexus.decision.integration.NexusDecisionContextBridge
import com.pantham.nexus.decision.model.*
import com.pantham.nexus.files.model.FileMetadata
import com.pantham.nexus.intelligence.model.NexusContext
import com.pantham.nexus.knowledge.*
import com.pantham.nexus.learning.context.NexusLearningContextAdapter
import com.pantham.nexus.learning.controller.NexusLearningController
import com.pantham.nexus.learning.engine.*
import com.pantham.nexus.learning.integration.*
import com.pantham.nexus.learning.model.*
import com.pantham.nexus.learning.repository.InMemoryLearningRepository
import com.pantham.nexus.learning.runtime.NexusAdaptiveLearningRuntime
import com.pantham.nexus.learning.security.NexusLearningPrivacyGate
import com.pantham.nexus.prediction.PredictiveContext
import com.pantham.nexus.situational.model.SituationalContext
import com.pantham.nexus.vision.model.VisionSource
import com.pantham.nexus.vision.model.VisualContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NexusAdaptiveLearningTests {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun createSampleAnalysis(recommendOptionId: String, recommendOptionName: String): DecisionAnalysis {
        val request = DecisionRequest(
            query = "Choose browser",
            intent = DecisionIntent.CHOOSE,
            options = listOf(
                DecisionOption("opt1", "Chrome"),
                DecisionOption("opt2", "Firefox")
            )
        )
        return DecisionAnalysis(
            request = request,
            scores = emptyList(),
            risks = emptyList(),
            tradeoffs = emptyList(),
            missingInformation = emptyList(),
            recommendation = DecisionRecommendation(
                optionId = recommendOptionId,
                optionName = recommendOptionName,
                confidence = DecisionConfidence.HIGH,
                score = 0.88f,
                reason = "Fastest",
                risks = emptyList(),
                tradeoffs = emptyList(),
                alternatives = emptyList()
            ),
            confidence = DecisionConfidence.HIGH
        )
    }

    // 1. Explicit preference learning
    @Test
    fun test_01_explicit_preference_learning() = runBlocking {
        val repo = InMemoryLearningRepository()
        val engine = NexusLearningFeedbackEngine(repo)
        val signal = LearningSignal(
            id = "s1",
            type = LearningSignalType.EXPLICIT_PREFERENCE,
            key = "language",
            value = "Hindi",
            confidence = 1.0f,
            scope = LearningScope.USER_PREFERENCE,
            source = "user_input"
        )
        val result = engine.record(signal)
        assertEquals(1, result.learnedPreferences.size)
        assertEquals("Hindi", result.learnedPreferences.first().value)
        assertEquals(PreferencePolarity.PREFER, result.learnedPreferences.first().polarity)
        assertTrue(result.learnedPreferences.first().confidence >= LearningConfidence.HIGH)
    }

    // 2. Weak implicit preference
    @Test
    fun test_02_weak_implicit_preference() = runBlocking {
        val repo = InMemoryLearningRepository()
        val engine = NexusLearningFeedbackEngine(repo)
        val signal = LearningSignal(
            id = "s2",
            type = LearningSignalType.IMPLICIT_PREFERENCE,
            key = "theme",
            value = "dark",
            confidence = 0.25f,
            scope = LearningScope.USER_PREFERENCE,
            source = "inference"
        )
        val result = engine.record(signal)
        assertTrue("Weak implicit signal should not be learned", result.learnedPreferences.isEmpty())
    }

    // 3. Repeated preference
    @Test
    fun test_03_repeated_preference() = runBlocking {
        val repo = InMemoryLearningRepository()
        val engine = NexusLearningFeedbackEngine(repo)
        val signal1 = LearningSignal(
            id = "s3_1",
            type = LearningSignalType.EXPLICIT_PREFERENCE,
            key = "diet",
            value = "vegetarian",
            confidence = 0.95f,
            scope = LearningScope.USER_PREFERENCE,
            source = "user_input"
        )
        engine.record(signal1)

        val signal2 = signal1.copy(id = "s3_2")
        val result2 = engine.record(signal2)

        assertEquals(1, result2.learnedPreferences.size)
        assertTrue(result2.learnedPreferences.first().evidenceCount >= 2)
        assertTrue(result2.learnedPreferences.first().strength >= 0.8f)
    }

    // 4. User correction
    @Test
    fun test_04_user_correction() = runBlocking {
        val repo = InMemoryLearningRepository()
        val engine = NexusLearningFeedbackEngine(repo)
        val signal = LearningSignal(
            id = "s4",
            type = LearningSignalType.USER_CORRECTION,
            key = "payment",
            value = "upi",
            confidence = 0.98f,
            scope = LearningScope.USER_PREFERENCE,
            source = "user_correction"
        )
        val result = engine.record(signal)
        assertEquals(1, result.learnedPreferences.size)
        assertEquals("upi", result.learnedPreferences.first().value)
        assertEquals(LearningConfidence.VERY_HIGH, result.learnedPreferences.first().confidence)
    }

    // 5. Decision acceptance
    @Test
    fun test_05_decision_acceptance() {
        val bridge = NexusDecisionLearningBridge()
        val analysis = createSampleAnalysis("opt1", "Chrome")
        val signal = bridge.createChoiceSignal(analysis, "opt1")
        assertEquals(LearningSignalType.USER_CHOICE, signal.type)
        assertEquals("Chrome", signal.value)
    }

    // 6. Decision override
    @Test
    fun test_06_decision_override() {
        val bridge = NexusDecisionLearningBridge()
        val analysis = createSampleAnalysis("opt1", "Chrome")
        val signal = bridge.createChoiceSignal(analysis, "opt2")
        assertEquals(LearningSignalType.DECISION_OVERRIDE, signal.type)
        assertEquals("Firefox", signal.value)
    }

    // 7. Decision rejection
    @Test
    fun test_07_decision_rejection() {
        val bridge = NexusDecisionLearningBridge()
        val analysis = createSampleAnalysis("opt1", "Chrome")
        val signal = bridge.createRejectionSignal(analysis, "opt1")
        assertEquals(LearningSignalType.USER_REJECTION, signal.type)
        assertEquals("Chrome", signal.value)
    }

    // 8. Success outcome
    @Test
    fun test_08_success_outcome() = runBlocking {
        val engine = NexusStrategyLearningEngine()
        val adj = engine.adjust("search.strategy", 0.50f, OutcomeStatus.SUCCESS, 0.90f)
        assertTrue(adj.newWeight > adj.oldWeight)
        assertEquals(0.545f, adj.newWeight, 0.001f)
    }

    // 9. Failure outcome
    @Test
    fun test_09_failure_outcome() = runBlocking {
        val engine = NexusStrategyLearningEngine()
        val adj = engine.adjust("search.strategy", 0.50f, OutcomeStatus.FAILURE, 0.90f)
        assertTrue(adj.newWeight < adj.oldWeight)
        assertEquals(0.437f, adj.newWeight, 0.001f)
    }

    // 10. Partial outcome
    @Test
    fun test_10_partial_outcome() = runBlocking {
        val engine = NexusStrategyLearningEngine()
        val adj = engine.adjust("search.strategy", 0.50f, OutcomeStatus.PARTIAL, 1.0f)
        assertEquals(0.475f, adj.newWeight, 0.001f)
    }

    // 11. Cancelled outcome
    @Test
    fun test_11_cancelled_outcome() = runBlocking {
        val engine = NexusStrategyLearningEngine()
        val adj = engine.adjust("search.strategy", 0.50f, OutcomeStatus.CANCELLED, 1.0f)
        assertEquals(0.490f, adj.newWeight, 0.001f)
    }

    // 12. Confidence calculation
    @Test
    fun test_12_confidence_calculation() {
        val relEngine = NexusLearningReliabilityEngine()
        val highSignal = LearningSignal("1", LearningSignalType.EXPLICIT_PREFERENCE, "k", "v", 1.0f, LearningScope.USER_PREFERENCE, "u")
        val lowSignal = LearningSignal("2", LearningSignalType.IMPLICIT_PREFERENCE, "k", "v", 0.3f, LearningScope.USER_PREFERENCE, "u")

        val highRel = relEngine.reliability(highSignal)
        val lowRel = relEngine.reliability(lowSignal)

        assertTrue(highRel >= 0.95f)
        assertTrue(lowRel < 0.20f)
    }

    // 13. Conflict resolution
    @Test
    fun test_13_conflict_resolution() = runBlocking {
        val repo = InMemoryLearningRepository()
        val engine = NexusLearningFeedbackEngine(repo)

        val explicitSignal = LearningSignal(
            id = "c1",
            type = LearningSignalType.EXPLICIT_PREFERENCE,
            key = "payment",
            value = "upi",
            confidence = 1.0f,
            scope = LearningScope.USER_PREFERENCE,
            source = "user"
        )
        engine.record(explicitSignal)

        val implicitConflictingSignal = LearningSignal(
            id = "c2",
            type = LearningSignalType.IMPLICIT_PREFERENCE,
            key = "payment",
            value = "card",
            confidence = 0.70f,
            scope = LearningScope.USER_PREFERENCE,
            source = "observation"
        )
        val result = engine.record(implicitConflictingSignal)

        assertTrue("Conflicting weaker implicit signal should be ignored or yield no learned preference",
            result.ignoredSignals.isNotEmpty() || result.learnedPreferences.isEmpty())
    }

    // 14. Recency
    @Test
    fun test_14_recency() {
        val relEngine = NexusLearningReliabilityEngine()
        val now = System.currentTimeMillis()
        val freshSignal = LearningSignal("r1", LearningSignalType.USER_CHOICE, "k", "v", 0.9f, LearningScope.USER_PREFERENCE, "u", timestamp = now)
        val oldSignal = LearningSignal("r2", LearningSignalType.USER_CHOICE, "k", "v", 0.9f, LearningScope.USER_PREFERENCE, "u", timestamp = now - 20L * 24L * 3600L * 1000L)

        val freshEv = relEngine.evidence(freshSignal, emptyList())
        val oldEv = relEngine.evidence(oldSignal, emptyList())

        assertTrue("Fresh signal should have higher recency than 20-day old signal", freshEv.recency > oldEv.recency)
    }

    // 15. Privacy blocking
    @Test
    fun test_15_privacy_blocking() {
        val gate = NexusLearningPrivacyGate()
        val sensitiveKeys = listOf("password", "user.passcode", "otp", "pin", "auth.token", "secret", "api_key", "card_number", "cvv")

        for (k in sensitiveKeys) {
            val signal = LearningSignal("s", LearningSignalType.EXPLICIT_PREFERENCE, k, "val123", 1f, LearningScope.USER_PREFERENCE, "u")
            assertNull("Key '$k' should be blocked by privacy gate", gate.sanitize(signal))
        }
    }

    // 16. Secret redaction
    @Test
    fun test_16_secret_redaction() {
        val gate = NexusLearningPrivacyGate()
        val signal = LearningSignal("s", LearningSignalType.EXPLICIT_PREFERENCE, "user_note", "my token=abc12345xyz is stored", 0.8f, LearningScope.USER_PREFERENCE, "u")
        val sanitized = gate.sanitize(signal)
        assertNotNull(sanitized)
        assertTrue(sanitized!!.value.contains("[REDACTED]"))
        assertFalse(sanitized.value.contains("abc12345xyz"))
    }

    // 17. Strategy increase
    @Test
    fun test_17_strategy_increase() {
        val engine = NexusStrategyLearningEngine()
        val adj = engine.adjust("planner.fast_path", 0.60f, OutcomeStatus.SUCCESS, 1.0f)
        assertEquals(0.65f, adj.newWeight, 0.001f)
    }

    // 18. Strategy decrease
    @Test
    fun test_18_strategy_decrease() {
        val engine = NexusStrategyLearningEngine()
        val adj = engine.adjust("planner.fast_path", 0.60f, OutcomeStatus.FAILURE, 1.0f)
        assertEquals(0.53f, adj.newWeight, 0.001f)
    }

    // 19. Bounded strategy weights
    @Test
    fun test_19_bounded_strategy_weights() {
        val engine = NexusStrategyLearningEngine()
        val highAdj = engine.adjust("k", 0.98f, OutcomeStatus.SUCCESS, 1.0f)
        assertTrue(highAdj.newWeight <= 1.0f)

        val lowAdj = engine.adjust("k", 0.02f, OutcomeStatus.FAILURE, 1.0f)
        assertTrue(lowAdj.newWeight >= 0.0f)
    }

    // 20. Decision Core integration
    @Test
    fun test_20_decision_core_integration() {
        val contextBridge = NexusDecisionContextBridge()
        val ext = DecisionExternalContext(
            learningEvidence = listOf("User historically prefers low cost flights")
        )
        val evidenceList = contextBridge.toEvidence(ext)
        assertEquals(1, evidenceList.size)
        assertEquals(EvidenceType.LEARNED_PREFERENCE, evidenceList.first().type)
        assertEquals(0.70f, evidenceList.first().reliability, 0.01f)
    }

    // 21. Knowledge Core integration
    @Test
    fun test_21_knowledge_core_integration() = runBlocking {
        val fakeKnowledgeRepo = object : NexusKnowledgeRepository {
            val observations = mutableListOf<KnowledgeObservation>()
            override suspend fun upsertEntity(entity: KnowledgeEntity) {}
            override suspend fun getEntity(id: String): KnowledgeEntity? = null
            override suspend fun findEntitiesByName(name: String, limit: Int): List<KnowledgeEntity> = emptyList()
            override suspend fun searchEntities(query: KnowledgeQuery): List<KnowledgeSearchResult> = emptyList()
            override suspend fun deactivateEntity(entityId: String) {}
            override suspend fun upsertRelationship(relationship: KnowledgeRelationship) {}
            override suspend fun getRelationship(id: String): KnowledgeRelationship? = null
            override suspend fun findRelationshipsFrom(entityId: String): List<KnowledgeRelationship> = emptyList()
            override suspend fun findRelationshipsTo(entityId: String): List<KnowledgeRelationship> = emptyList()
            override suspend fun findRelationships(entityId: String, type: RelationshipType?): List<KnowledgeRelationship> = emptyList()
            override suspend fun deleteRelationship(relationshipId: String) {}
            override suspend fun addObservation(observation: KnowledgeObservation) {
                observations.add(observation)
            }
            override suspend fun getObservations(entityId: String, limit: Int): List<KnowledgeObservation> = emptyList()
            override suspend fun findObservations(predicate: String, value: String?, limit: Int): List<KnowledgeObservation> = emptyList()
            override suspend fun getRecentEntities(limit: Int): List<KnowledgeEntity> = emptyList()
            override suspend fun getImportantEntities(limit: Int): List<KnowledgeEntity> = emptyList()
            override suspend fun findConflictingFacts(entityId: String): List<KnowledgeConflict> = emptyList()
            override suspend fun clearAll() {}
        }

        val bridge = NexusLearningKnowledgeBridgeImpl(fakeKnowledgeRepo)
        val pref = LearnedPreference(
            key = "language",
            value = "Hindi",
            polarity = PreferencePolarity.PREFER,
            strength = 0.95f,
            confidence = LearningConfidence.VERY_HIGH,
            evidenceCount = 3,
            lastUpdated = System.currentTimeMillis(),
            scope = LearningScope.USER_PREFERENCE
        )

        bridge.publishPreference(pref)
        assertEquals(1, fakeKnowledgeRepo.observations.size)
        assertEquals("PREFERS", fakeKnowledgeRepo.observations.first().predicate)
        assertTrue(fakeKnowledgeRepo.observations.first().value.contains("Hindi"))
    }

    // 22. Prediction Core integration
    @Test
    fun test_22_prediction_core_integration() = runBlocking {
        val bridge = NexusLearningPredictionBridgeImpl()
        val pref = LearnedPreference(
            key = "ui.mode",
            value = "dark",
            polarity = PreferencePolarity.PREFER,
            strength = 0.85f,
            confidence = LearningConfidence.HIGH,
            evidenceCount = 2,
            lastUpdated = System.currentTimeMillis(),
            scope = LearningScope.USER_PREFERENCE
        )
        bridge.applyPreference(pref)
        assertEquals(1, bridge.supportingHints.size)
        assertTrue(bridge.supportingHints.first().contains("dark"))
        assertTrue(bridge.supportingHints.first().contains("historically prefers"))
    }

    // 23. Context integration
    @Test
    fun test_23_context_integration() = runBlocking {
        val runtime = NexusAdaptiveLearningRuntime.getInstance()
        runtime.recordSignal(
            LearningSignal("c_ctx", LearningSignalType.EXPLICIT_PREFERENCE, "nav", "metro", 1f, LearningScope.USER_PREFERENCE, "test")
        )
        val adapter = NexusLearningContextAdapter(runtime)
        val lCtx = adapter.buildContext()
        assertNotNull(lCtx)

        val nCtx = NexusContext(
            userInput = "Where to go",
            adaptiveLearningContext = lCtx
        )
        assertNotNull(nCtx.adaptiveLearningContext)
        assertTrue(nCtx.adaptiveLearningContext!!.preferences.any { it.key == "nav" })
    }

    // 24. MultiTurn integration
    @Test
    fun test_24_multi_turn_integration() = runBlocking {
        val hardwareController = HardwareController(context, CoroutineScope(Dispatchers.Unconfined))
        val contextEngine = NexusContextEngine(context, hardwareController)
        val sessionManager = MultiTurnSessionManager(contextEngine)
        val bridge = NexusLearningMultiTurnBridge()

        val handled = bridge.processTurnFeedback("मुझे सस्ता वाला चाहिए", sessionManager)
        assertTrue(handled)
    }

    // 25. Action separation
    @Test
    fun test_25_action_separation() {
        val controller = NexusLearningController()
        val methods = controller.javaClass.declaredMethods.map { it.name }
        assertFalse(methods.contains("execute"))
        assertFalse(methods.contains("dispatchAction"))
        assertFalse(methods.contains("bypassGate"))
    }

    // 26. Current explicit constraint overrides learned preference
    @Test
    fun test_26_current_explicit_constraint_overrides_learned_preference() {
        val constraintEngine = NexusDecisionConstraintEngine()

        val cheapOption = DecisionOption(
            id = "opt_cheap",
            name = "Cheap Cafe",
            attributes = mapOf("price" to "low", "diet" to "non_veg")
        )
        val vegOption = DecisionOption(
            id = "opt_veg",
            name = "Pure Veg Restaurant",
            attributes = mapOf("price" to "medium", "diet" to "vegetarian")
        )

        val currentHardConstraint = DecisionConstraint(
            name = "diet",
            value = "vegetarian",
            importance = 1.0f,
            hardConstraint = true
        )

        val satisfiesCheap = constraintEngine.satisfies(cheapOption, currentHardConstraint)
        val satisfiesVeg = constraintEngine.satisfies(vegOption, currentHardConstraint)

        assertFalse("Option violating current hard constraint must be rejected despite any learned low-price preference", satisfiesCheap)
        assertTrue("Option satisfying current hard constraint must be accepted", satisfiesVeg)
    }

    // 27. Regression: Decision Intelligence
    @Test
    fun test_27_regression_decision_intelligence() {
        val engine = NexusDecisionEngine()
        val request = DecisionRequest(
            query = "Compare phone A and phone B",
            intent = DecisionIntent.COMPARE,
            options = listOf(
                DecisionOption("p1", "Phone A", attributes = mapOf("camera" to "excellent")),
                DecisionOption("p2", "Phone B", attributes = mapOf("camera" to "good"))
            ),
            evidence = listOf(
                DecisionEvidence(EvidenceType.LEARNED_PREFERENCE, statement = "User values camera quality", reliability = 0.8f, relevance = 0.8f)
            )
        )
        val analysis = engine.analyze(request)
        assertNotNull(analysis.recommendation)
        assertEquals("p1", analysis.recommendation?.optionId)
    }

    // 28. Regression: Prediction Core
    @Test
    fun test_28_regression_prediction_core() {
        val pCtx = PredictiveContext(
            predictions = emptyList(),
            internalHints = listOf("hint1"),
            proactiveCandidates = emptyList()
        )
        assertEquals(1, pCtx.internalHints.size)
    }

    // 29. Regression: Knowledge Core
    @Test
    fun test_29_regression_knowledge_core() {
        val kCtx = KnowledgeContext(
            relevantEntities = listOf(
                KnowledgeEntity(id = "k_reg", type = KnowledgeEntityType.PERSON, canonicalName = "John", source = KnowledgeSource.CONVERSATION)
            ),
            relevantRelationships = emptyList(),
            relevantObservations = emptyList()
        )
        assertEquals(1, kCtx.relevantEntities.size)
    }

    // 30. Regression: Situational Awareness
    @Test
    fun test_30_regression_situational_awareness() {
        val sCtx = SituationalContext()
        assertEquals(com.pantham.nexus.situational.model.SituationState.UNKNOWN, sCtx.activeState)
    }

    // 31. Regression: Semantic File Intelligence
    @Test
    fun test_31_regression_semantic_file_intelligence() {
        val meta = FileMetadata("f1", "content://file1", "document.pdf", "pdf")
        assertEquals("document.pdf", meta.name)
    }

    // 32. Regression: Vision Core
    @Test
    fun test_32_regression_vision_core() {
        val vCtx = VisualContext(
            frameId = "vf_reg",
            source = VisionSource.CAMERA,
            description = "Book on desk",
            visibleText = "Android Development",
            detectedObjects = emptyList(),
            detectedLabels = emptyList(),
            barcodes = emptyList(),
            timestamp = 3000L
        )
        assertEquals("Android Development", vCtx.visibleText)
    }
}
