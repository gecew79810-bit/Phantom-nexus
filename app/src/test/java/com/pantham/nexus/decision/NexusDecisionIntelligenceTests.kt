package com.pantham.nexus.decision

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.action.NexusAction
import com.example.ai.dialogue.MultiTurnResult
import com.example.ai.dialogue.MultiTurnSessionManager
import com.example.ai.dialogue.MultiTurnState
import com.example.context.NexusContextEngine
import com.example.hardware.HardwareController
import com.example.voice.AssistantLanguage
import com.pantham.nexus.decision.adapter.*
import com.pantham.nexus.decision.engine.*
import com.pantham.nexus.decision.integration.DecisionExternalContext
import com.pantham.nexus.decision.integration.NexusDecisionContextBridge
import com.pantham.nexus.decision.integration.NexusDecisionIntelligenceBridge
import com.pantham.nexus.decision.model.*
import com.pantham.nexus.decision.parser.NexusDecisionQueryParser
import com.pantham.nexus.decision.runtime.NexusDecisionRuntime
import com.pantham.nexus.decision.security.NexusDecisionSafetyGate
import com.pantham.nexus.files.model.FileMetadata
import com.pantham.nexus.files.model.FileSearchResult
import com.pantham.nexus.intelligence.context.NexusContextAssembler
import com.pantham.nexus.intelligence.context.NexusRuntimeContextProvider
import com.pantham.nexus.intelligence.context.RelevantMemoryResolver
import com.pantham.nexus.intelligence.model.*
import com.pantham.nexus.knowledge.KnowledgeContext
import com.pantham.nexus.knowledge.KnowledgeEntity
import com.pantham.nexus.knowledge.KnowledgeEntityType
import com.pantham.nexus.knowledge.KnowledgeSource
import com.pantham.nexus.prediction.PredictiveContext
import com.pantham.nexus.situational.model.SituationalContext
import com.pantham.nexus.vision.model.VisionObject
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
class NexusDecisionIntelligenceTests {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    // 1. Decision Intent
    @Test
    fun test_1_decision_intent() {
        val parser = NexusDecisionQueryParser()
        assertEquals(DecisionIntent.COMPARE, parser.detectIntent("compare these two options"))
        assertEquals(DecisionIntent.CHOOSE, parser.detectIntent("mere liye kaunsa better hai"))
        assertEquals(DecisionIntent.RECOMMEND, parser.detectIntent("please recommend a phone"))
        assertEquals(DecisionIntent.EVALUATE, parser.detectIntent("pros and cons of solar inverter"))
        assertEquals(DecisionIntent.TRADEOFF, parser.detectIntent("what is the trade-off here"))

        val adapter = NexusDecisionIntentAdapter()
        val canonicalResearch = IntentResult(
            type = IntentType.RESEARCH,
            confidence = 0.9,
            confidenceLevel = ConfidenceLevel.HIGH
        )
        assertEquals(DecisionIntent.CHOOSE, adapter.adapt(canonicalResearch, "which one is better"))
    }

    // 2. Option Extraction
    @Test
    fun test_2_option_extraction() {
        val parser = NexusDecisionQueryParser()
        val options1 = parser.extractOptionNames("iPhone 15 vs Galaxy S24")
        assertEquals(2, options1.size)
        assertEquals("iPhone 15", options1[0])
        assertEquals("Galaxy S24", options1[1])

        val optionsHindi = parser.extractOptionNames("Samsung ya Apple ya OnePlus")
        assertTrue(optionsHindi.size >= 3)
    }

    // 3. Constraint Handling
    @Test
    fun test_3_constraint_handling() {
        val engine = NexusDecisionConstraintEngine()
        val option = DecisionOption(
            id = "opt1",
            name = "Inverter A",
            attributes = mapOf("warranty" to "5 years", "budget" to "medium")
        )
        val constraint = DecisionConstraint(
            name = "warranty",
            value = "5 years",
            importance = 0.8f,
            hardConstraint = false
        )
        assertTrue(engine.satisfies(option, constraint))
        assertNull(engine.violationReason(option, constraint))
    }

    // 4. Hard Constraint Blocking
    @Test
    fun test_4_hard_constraint_blocking() {
        val engine = NexusDecisionConstraintEngine()
        val option = DecisionOption(
            id = "a",
            name = "Option A",
            attributes = mapOf("budget" to "high")
        )
        val hardConstraint = DecisionConstraint(
            name = "budget",
            value = "low",
            importance = 1f,
            hardConstraint = true
        )
        assertFalse(engine.satisfies(option, hardConstraint))
        val reason = engine.violationReason(option, hardConstraint)
        assertNotNull(reason)
        assertTrue(reason!!.contains("required value 'low'"))
    }

    // 5. Evidence Scoring
    @Test
    fun test_5_evidence_scoring() {
        val engine = NexusDecisionEvidenceEngine()
        val evidence = DecisionEvidence(
            type = EvidenceType.FILE,
            statement = "Verified invoice document",
            reliability = 0.9f,
            relevance = 0.9f
        )
        val score = engine.evaluateEvidence(listOf(evidence))
        assertTrue(score > 0.8f)
    }

    // 6. Weak Evidence
    @Test
    fun test_6_weak_evidence() {
        val engine = NexusDecisionEvidenceEngine()
        val weak = DecisionEvidence(
            type = EvidenceType.INFERRED,
            statement = "Unverified rumor",
            reliability = 0.15f,
            relevance = 0.20f
        )
        val score = engine.evaluateEvidence(listOf(weak))
        assertTrue(score < 0.35f)
    }

    // 7. Strong Evidence
    @Test
    fun test_7_strong_evidence() {
        val engine = NexusDecisionEvidenceEngine()
        val strong = DecisionEvidence(
            type = EvidenceType.EXPLICIT_USER_FACT,
            statement = "User bank balance verified statement",
            reliability = 0.98f,
            relevance = 0.95f
        )
        val score = engine.evaluateEvidence(listOf(strong))
        assertTrue(score >= 0.90f)
    }

    // 8. Risk Detection
    @Test
    fun test_8_risk_detection() {
        val riskEngine = NexusDecisionRiskEngine()
        val optionNoEvidence = DecisionOption(id = "opt_blind", name = "Blind Option")
        val risks = riskEngine.assess(
            request = DecisionRequest(query = "choose"),
            option = optionNoEvidence
        )
        assertTrue(risks.isNotEmpty())
        assertTrue(risks.any { it.description.contains("Limited supporting evidence") })
    }

    // 9. Trade-off Detection
    @Test
    fun test_9_trade_off_detection() {
        val tradeoffEngine = NexusDecisionTradeoffEngine()
        val scores = listOf(
            DecisionScore("a", "price", 1.0f, 0.5f, "A is cheap"),
            DecisionScore("b", "price", 0.4f, 0.2f, "B is expensive"),
            DecisionScore("a", "quality", 0.4f, 0.2f, "A quality lower"),
            DecisionScore("b", "quality", 1.0f, 0.5f, "B quality premium")
        )
        val criteria = listOf(
            DecisionCriterion("price", "Price", 0.5f),
            DecisionCriterion("quality", "Quality", 0.5f)
        )
        val options = listOf(
            DecisionOption("a", "Budget Model A"),
            DecisionOption("b", "Premium Model B")
        )

        val tradeoffs = tradeoffEngine.detect(scores, criteria, options)
        assertTrue(tradeoffs.isNotEmpty())
        assertTrue(tradeoffs.any { it.criterion == "Price" })
    }

    // 10. Missing Information
    @Test
    fun test_10_missing_information() {
        val missingEngine = NexusDecisionMissingInfoEngine()
        val singleOptionReq = DecisionRequest(
            query = "which is better",
            options = listOf(DecisionOption("a", "Only Option"))
        )
        val missing = missingEngine.detect(singleOptionReq, singleOptionReq.options, emptyList())
        assertTrue(missing.any { it.blocking && it.field == "comparison options" })
    }

    // 11. Recommendation Ranking
    @Test
    fun test_11_recommendation_ranking() {
        val request = DecisionRequest(
            query = "which is better",
            options = listOf(
                DecisionOption(
                    id = "a",
                    name = "Model A",
                    evidence = listOf(
                        DecisionEvidence(EvidenceType.EXTERNAL, statement = "Solid", reliability = 0.95f, relevance = 0.95f)
                    )
                ),
                DecisionOption(
                    id = "b",
                    name = "Model B",
                    evidence = listOf(
                        DecisionEvidence(EvidenceType.EXTERNAL, statement = "Weak", reliability = 0.30f, relevance = 0.30f)
                    )
                )
            )
        )

        val analysis = NexusDecisionEngine().analyze(request)
        assertNotNull(analysis.recommendation)
        assertEquals("a", analysis.recommendation?.optionId)
        assertEquals("Model A", analysis.recommendation?.optionName)
        assertEquals(1, analysis.recommendation?.alternatives?.size)
        assertEquals("b", analysis.recommendation?.alternatives?.first()?.optionId)
    }

    // 12. Confidence Calculation
    @Test
    fun test_12_confidence_calculation() {
        val engine = NexusDecisionEngine()
        val strongRequest = DecisionRequest(
            query = "which is better",
            options = listOf(
                DecisionOption(
                    id = "x",
                    name = "Verified Strong Option",
                    evidence = listOf(
                        DecisionEvidence(EvidenceType.FILE, statement = "Signed contract", reliability = 0.99f, relevance = 0.95f)
                    )
                ),
                DecisionOption(
                    id = "y",
                    name = "Verified Secondary Option",
                    evidence = listOf(
                        DecisionEvidence(EvidenceType.FILE, statement = "Old contract", reliability = 0.70f, relevance = 0.70f)
                    )
                )
            )
        )

        val analysis = engine.analyze(strongRequest)
        assertTrue(analysis.confidence >= DecisionConfidence.HIGH)
    }

    // 13. Safety Gate
    @Test
    fun test_13_safety_gate() {
        val gate = NexusDecisionSafetyGate()
        val criticalRisk = DecisionRiskItem(
            optionId = "opt_danger",
            description = "High financial loss risk",
            probability = 0.95f,
            impact = 0.98f
        )
        val unsafeAnalysis = DecisionAnalysis(
            request = DecisionRequest(query = "invest all"),
            scores = emptyList(),
            risks = listOf(criticalRisk),
            tradeoffs = emptyList(),
            missingInformation = emptyList(),
            recommendation = DecisionRecommendation(
                optionId = "opt_danger",
                optionName = "Danger Option",
                confidence = DecisionConfidence.HIGH,
                score = 0.9f,
                reason = "High returns",
                risks = listOf(criticalRisk),
                tradeoffs = emptyList(),
                alternatives = emptyList()
            ),
            confidence = DecisionConfidence.HIGH
        )

        // Safety gate must reject false certainty for critical risk
        assertFalse(gate.evaluate(unsafeAnalysis))
        assertEquals(DecisionRisk.CRITICAL, gate.riskLevel(unsafeAnalysis))
    }

    // 14. File Evidence Integration
    @Test
    fun test_14_file_evidence_integration() {
        val resolver = NexusDecisionOptionResolver()
        val fileResult = FileSearchResult(
            fileId = "file_123",
            name = "warranty_plan.pdf",
            uri = "content://file_123",
            matchScore = 0.88f,
            snippet = "5 year comprehensive warranty covered"
        )

        val options = resolver.resolveFromFileResults(listOf(fileResult))
        assertEquals(1, options.size)
        assertEquals("file_123", options[0].id)
        assertEquals(EvidenceType.FILE, options[0].evidence[0].type)
        assertTrue(options[0].evidence[0].statement.contains("5 year comprehensive warranty"))
    }

    // 15. Knowledge Evidence Integration
    @Test
    fun test_15_knowledge_evidence_integration() {
        val resolver = NexusDecisionOptionResolver()
        val knowledgeEntity = KnowledgeEntity(
            id = "ke_device",
            type = KnowledgeEntityType.DEVICE,
            canonicalName = "MacBook Pro M3",
            source = KnowledgeSource.CONVERSATION
        )

        val options = resolver.resolveFromKnowledge(listOf(knowledgeEntity))
        assertEquals(1, options.size)
        assertEquals("MacBook Pro M3", options[0].name)
        assertEquals(EvidenceType.KNOWLEDGE, options[0].evidence[0].type)
    }

    // 16. Vision Evidence Integration
    @Test
    fun test_16_vision_evidence_integration() {
        val resolver = NexusDecisionOptionResolver()
        val visualContext = VisualContext(
            frameId = "vf_100",
            source = VisionSource.CAMERA,
            description = "Two cereal boxes on counter",
            visibleText = "Oat Crunch Cereal and Corn Flakes",
            detectedObjects = listOf(
                VisionObject(label = "Oat Crunch Cereal", confidence = 0.89f),
                VisionObject(label = "Corn Flakes", confidence = 0.84f)
            ),
            detectedLabels = emptyList(),
            barcodes = emptyList(),
            timestamp = System.currentTimeMillis()
        )

        val options = resolver.resolveFromVision(visualContext)
        assertEquals(2, options.size)
        assertEquals("Oat Crunch Cereal", options[0].name)
        assertEquals(EvidenceType.VISION, options[0].evidence[0].type)
    }

    // 17. Prediction Evidence Integration
    @Test
    fun test_17_prediction_evidence_integration() {
        val bridge = NexusDecisionContextBridge()
        val extContext = DecisionExternalContext(
            predictionEvidence = listOf("User usually prefers energy saving mode on weekdays")
        )
        val evidence = bridge.toEvidence(extContext)
        assertEquals(1, evidence.size)
        assertEquals(EvidenceType.PREDICTION, evidence[0].type)
        assertEquals(0.45f, evidence[0].reliability) // Supporting evidence only
    }

    // 18. Situational Evidence Integration
    @Test
    fun test_18_situational_evidence_integration() {
        val bridge = NexusDecisionContextBridge()
        val extContext = DecisionExternalContext(
            situationEvidence = listOf("User is driving and battery is below 15%")
        )
        val evidence = bridge.toEvidence(extContext)
        assertEquals(1, evidence.size)
        assertEquals(EvidenceType.SITUATION, evidence[0].type)
    }

    // 19. NexusContext Integration
    @Test
    fun test_19_nexus_context_integration() = runBlocking {
        val decisionAdapter = NexusDecisionContextAdapter()
        val sampleAnalysis = DecisionAnalysis(
            request = DecisionRequest(query = "compare laptops"),
            scores = emptyList(),
            risks = emptyList(),
            tradeoffs = emptyList(),
            missingInformation = emptyList(),
            recommendation = null,
            confidence = DecisionConfidence.MEDIUM
        )
        decisionAdapter.updateActiveDecision(
            analysis = sampleAnalysis,
            fileIds = listOf("file_1"),
            knowledgeIds = listOf("k_1")
        )

        val dummyRuntime = object : NexusRuntimeContextProvider {
            override suspend fun currentPackage(): String? = "com.test"
            override suspend fun currentScreenSummary(): String? = null
            override suspend fun currentLocation(): String? = null
            override suspend fun batteryPercent(): Int? = 80
            override suspend fun networkAvailable(): Boolean = true
            override suspend fun currentMedia(): String? = null
            override suspend fun recentEntities(): List<ResolvedEntity> = emptyList()
        }
        val dummyMemory = object : RelevantMemoryResolver {
            override suspend fun findRelevantMemories(input: String, limit: Int): List<MemoryCandidate> = emptyList()
        }

        val assembler = NexusContextAssembler(
            runtimeProvider = dummyRuntime,
            memoryResolver = dummyMemory,
            decisionAdapter = decisionAdapter
        )

        val assembled = assembler.build(
            conversationId = "c1",
            taskId = null,
            input = "compare laptops",
            channel = InputChannel.TEXT
        )

        assertNotNull(assembled.decisionContext)
        assertNotNull(assembled.decisionContext?.activeDecision)
        assertEquals(1, assembled.decisionContext?.relevantFiles?.size)
    }

    // 20. MultiTurnSessionManager Missing-Info Flow
    @Test
    fun test_20_multi_turn_missing_info_flow() {
        val hardwareController = HardwareController(context, CoroutineScope(Dispatchers.Unconfined))
        val contextEngine = NexusContextEngine(context, hardwareController)
        val sessionManager = MultiTurnSessionManager(contextEngine)
        val bridge = NexusDecisionMultiTurnBridge()

        val missing = listOf(
            MissingDecisionInformation(
                field = "budget",
                reason = "Price is critical and unspecified",
                importance = 0.9f,
                blocking = true
            )
        )

        val prompt = bridge.requestMissingInformation(
            sessionManager = sessionManager,
            missing = missing,
            language = AssistantLanguage.HINDI
        )

        assertEquals("आपका बजट कितना है?", prompt)
        assertTrue(sessionManager.currentState is MultiTurnState.AwaitingSlot)
        val awaiting = sessionManager.currentState as MultiTurnState.AwaitingSlot
        assertEquals("DECISION", awaiting.intent)
        assertEquals("budget", awaiting.missingSlot)

        // Process answer follow-up
        val followUp = sessionManager.processFollowUp("25000 rupees", AssistantLanguage.HINDI)
        assertTrue(followUp.handled)
        assertTrue(sessionManager.currentState is MultiTurnState.Idle)
    }

    // 21. EntityResolver Option Resolution
    @Test
    fun test_21_entity_resolver_option_resolution() {
        val resolver = NexusDecisionOptionResolver()
        val entities = listOf(
            ResolvedEntity(id = "e1", name = "Pixel 8", type = "SMARTPHONE", value = "128GB"),
            ResolvedEntity(id = "e2", name = "iPhone 15", type = "SMARTPHONE", value = "128GB")
        )

        val options = resolver.resolveFromEntities(entities)
        assertEquals(2, options.size)
        assertEquals("Pixel 8", options[0].name)
        assertEquals("iPhone 15", options[1].name)
    }

    // 22. ActionRouter Separation
    @Test
    fun test_22_action_router_separation() {
        val actionBridge = NexusDecisionActionBridge(context)
        assertTrue(actionBridge.isActionFollowUp("choose it"))
        assertTrue(actionBridge.isActionFollowUp("buy it now"))
        assertTrue(actionBridge.isActionFollowUp("yeh chuno"))
        assertFalse(actionBridge.isActionFollowUp("tell me more"))
    }

    // 23. Permission/Confirmation Separation
    @Test
    fun test_23_permission_confirmation_separation() = runBlocking {
        val actionBridge = NexusDecisionActionBridge(context)
        val mockAnalysis = DecisionAnalysis(
            request = DecisionRequest("choose"),
            scores = emptyList(),
            risks = emptyList(),
            tradeoffs = emptyList(),
            missingInformation = emptyList(),
            recommendation = DecisionRecommendation(
                optionId = "opt_1",
                optionName = "Firefox",
                confidence = DecisionConfidence.HIGH,
                score = 0.9f,
                reason = "Preferred",
                risks = emptyList(),
                tradeoffs = emptyList(),
                alternatives = emptyList()
            ),
            confidence = DecisionConfidence.HIGH
        )

        val result = actionBridge.handleActionFollowUp(
            userInput = "open it",
            activeAnalysis = mockAnalysis
        )

        // Verifies action goes through permission pipeline
        assertNotNull(result)
        assertTrue(result is DecisionActionResult.ActionRouted || result is DecisionActionResult.RequiresConfirmation)
    }

    // 24. Regression: Intelligence Core
    @Test
    fun test_24_regression_intelligence_core() {
        val ctx = NexusContext(
            userInput = "hello",
            inputChannel = InputChannel.VOICE
        )
        assertEquals(InputChannel.VOICE, ctx.inputChannel)
    }

    // 25. Regression: Knowledge Core
    @Test
    fun test_25_regression_knowledge_core() {
        val kCtx = KnowledgeContext(
            relevantEntities = listOf(
                KnowledgeEntity(
                    id = "k1",
                    type = KnowledgeEntityType.PERSON,
                    canonicalName = "Amit",
                    source = KnowledgeSource.CONVERSATION
                )
            ),
            relevantRelationships = emptyList(),
            relevantObservations = emptyList()
        )
        assertEquals(1, kCtx.relevantEntities.size)
    }

    // 26. Regression: Vision Core
    @Test
    fun test_26_regression_vision_core() {
        val vCtx = VisualContext(
            frameId = "f1",
            source = VisionSource.SCREEN,
            description = "Homescreen",
            visibleText = "",
            detectedObjects = emptyList(),
            detectedLabels = emptyList(),
            barcodes = emptyList(),
            timestamp = 1000L
        )
        assertEquals(VisionSource.SCREEN, vCtx.source)
    }

    // 27. Regression: Prediction Core
    @Test
    fun test_27_regression_prediction_core() {
        val pCtx = PredictiveContext(
            predictions = emptyList(),
            internalHints = emptyList(),
            proactiveCandidates = emptyList()
        )
        assertTrue(pCtx.predictions.isEmpty())
    }

    // 28. Regression: Situational Awareness
    @Test
    fun test_28_regression_situational_awareness() {
        val sCtx = SituationalContext()
        assertEquals(com.pantham.nexus.situational.model.SituationState.UNKNOWN, sCtx.activeState)
    }

    // 29. Regression: Semantic File Intelligence
    @Test
    fun test_29_regression_semantic_file_intelligence() {
        val meta = FileMetadata("f1", "content://file1", "document.pdf", "pdf")
        assertEquals("document.pdf", meta.name)
    }
}
