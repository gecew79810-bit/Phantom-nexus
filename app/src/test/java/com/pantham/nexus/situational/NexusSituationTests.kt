package com.pantham.nexus.situational

import com.pantham.nexus.intelligence.context.NexusRuntimeContextProvider
import com.pantham.nexus.intelligence.context.RelevantMemoryResolver
import com.pantham.nexus.intelligence.context.NexusContextAssembler
import com.pantham.nexus.intelligence.model.InputChannel
import com.pantham.nexus.intelligence.model.MemoryCandidate
import com.pantham.nexus.intelligence.model.ResolvedEntity
import com.pantham.nexus.situational.adapters.*
import com.pantham.nexus.situational.engine.*
import com.pantham.nexus.situational.integration.NexusSituationContextAdapter
import com.pantham.nexus.situational.model.*
import com.pantham.nexus.situational.security.NexusSituationPrivacyGate
import com.pantham.nexus.situational.signal.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NexusSituationTests {

    private class MockRuntimeContextProvider(
        var currentPkg: String? = "com.google.android.apps.maps",
        var currentScreen: String? = "Navigating to home",
        var location: String? = "New Delhi",
        var battery: Int? = 85,
        var network: Boolean = true
    ) : NexusRuntimeContextProvider {
        override suspend fun currentPackage(): String? = currentPkg
        override suspend fun currentScreenSummary(): String? = currentScreen
        override suspend fun currentLocation(): String? = location
        override suspend fun batteryPercent(): Int? = battery
        override suspend fun networkAvailable(): Boolean = network
        override suspend fun currentMedia(): String? = null
        override suspend fun recentEntities(): List<ResolvedEntity> = emptyList()
    }

    private class MockMemoryResolver : RelevantMemoryResolver {
        override suspend fun findRelevantMemories(input: String, limit: Int): List<MemoryCandidate> = emptyList()
    }

    // 1. Device provider adaptation
    @Test
    fun deviceProviderAdaptation() = runBlocking {
        val mockRuntime = MockRuntimeContextProvider(battery = 12, network = true)
        val provider = ContextDrivenDeviceSituationProvider(mockRuntime)
        val situation = provider.getDeviceSituation()

        assertEquals(12, situation.batteryPercent)
        assertTrue(situation.batteryLow)
        assertTrue(situation.wifiConnected)
        assertTrue(situation.screenOn)
    }

    // 2. Foreground app adaptation
    @Test
    fun foregroundAppAdaptation() = runBlocking {
        val mockRuntime = MockRuntimeContextProvider(currentPkg = "com.google.android.apps.maps")
        val provider = ContextDrivenAppSituationProvider(mockRuntime)
        val situation = provider.getAppSituation()

        assertEquals("com.google.android.apps.maps", situation.packageName)
        assertEquals("navigation", situation.category)
        assertTrue(situation.foreground)
    }

    // 3. Network adaptation
    @Test
    fun networkAdaptation() = runBlocking {
        val mockRuntime = MockRuntimeContextProvider(network = false)
        val provider = ContextDrivenNetworkSituationProvider(mockRuntime)
        val situation = provider.getNetworkSituation()

        assertFalse(situation.connected)
        assertNull(situation.transport)
    }

    // 4. Voice/session adaptation
    @Test
    fun voiceSessionAdaptation() = runBlocking {
        val provider = StateDrivenSessionSituationProvider(
            isListeningProvider = { true },
            isSpeakingProvider = { false },
            activeSessionIdProvider = { "sess_123" }
        )
        val situation = provider.getSessionSituation()

        assertTrue(situation.active)
        assertTrue(situation.listening)
        assertFalse(situation.speaking)
        assertTrue(situation.voiceMode)
        assertEquals("sess_123", situation.sessionId)
    }

    // 5. Notification metadata filtering
    @Test
    fun notificationMetadataFiltering() = runBlocking {
        val provider = StateDrivenNotificationSituationProvider(
            unreadCountProvider = { 4 },
            urgentCountProvider = { 1 },
            latestPackageProvider = { "com.whatsapp" },
            hasActionableProvider = { true }
        )
        val situation = provider.getNotificationSituation()

        assertEquals(4, situation.unreadCount)
        assertEquals(1, situation.urgentCount)
        assertEquals("com.whatsapp", situation.latestPackage)
        assertTrue(situation.hasActionableNotification)
    }

    // 6. Active goal adaptation
    @Test
    fun activeGoalAdaptation() = runBlocking {
        val provider = StateDrivenGoalSituationProvider(
            goalProvider = {
                GoalSituation(
                    active = true,
                    goalId = "goal_99",
                    title = "Book cab to airport",
                    progress = 0.5f,
                    blocked = false
                )
            }
        )
        val situation = provider.getGoalSituation()

        assertTrue(situation.active)
        assertEquals("goal_99", situation.goalId)
        assertEquals(0.5f, situation.progress, 0.01f)
    }

    // 7. Pending action adaptation
    @Test
    fun pendingActionAdaptation() = runBlocking {
        val provider = StateDrivenActionSituationProvider(
            actionProvider = {
                ActionSituation(
                    pending = true,
                    actionId = "act_42",
                    description = "Send payment",
                    requiresConfirmation = true,
                    riskLevel = "HIGH"
                )
            }
        )
        val situation = provider.getActionSituation()

        assertTrue(situation.pending)
        assertEquals("act_42", situation.actionId)
        assertTrue(situation.requiresConfirmation)
        assertEquals("HIGH", situation.riskLevel)
    }

    // 8. Prediction adaptation
    @Test
    fun predictionAdaptation() = runBlocking {
        val provider = ContextDrivenPredictionSituationProvider(
            predictiveContextProvider = {
                com.pantham.nexus.prediction.PredictiveContext(
                    predictions = listOf(
                        com.pantham.nexus.prediction.NexusPrediction(
                            type = com.pantham.nexus.prediction.PredictionType.NEXT_INTENT,
                            title = "Navigate to destination",
                            description = "Next route step",
                            confidence = com.pantham.nexus.prediction.PredictionConfidence.HIGH,
                            confidenceScore = 0.88f,
                            disposition = com.pantham.nexus.prediction.PredictionDisposition.SUGGEST
                        )
                    ),
                    internalHints = emptyList(),
                    proactiveCandidates = emptyList()
                )
            }
        )
        val situation = provider.getPredictionSituation()

        assertEquals("Navigate to destination", situation.strongestPrediction)
        assertEquals(0.88f, situation.confidence, 0.01f)
        assertEquals(1, situation.predictionCount)
    }

    // 9. Vision adaptation
    @Test
    fun visionAdaptation() = runBlocking {
        val provider = ContextDrivenVisionSituationProvider(
            visualContextProvider = {
                com.pantham.nexus.vision.model.VisualContext(
                    frameId = "frame_1",
                    source = com.pantham.nexus.vision.model.VisionSource.SCREEN,
                    description = "A document containing flight ticket",
                    visibleText = "Flight AI 101 Gate 4B",
                    detectedObjects = emptyList(),
                    detectedLabels = emptyList(),
                    barcodes = emptyList(),
                    timestamp = System.currentTimeMillis()
                )
            }
        )
        val situation = provider.getVisionSituation()

        assertTrue(situation.available)
        assertEquals("A document containing flight ticket", situation.summary)
        assertTrue(situation.textDetected)
    }

    // 10. Temporal adaptation
    @Test
    fun temporalAdaptation() = runBlocking {
        val provider = ContextDrivenTemporalSituationProvider(
            temporalProvider = {
                TemporalSituation(
                    currentLabel = "Morning",
                    upcomingEvent = "Flight to Mumbai",
                    minutesUntilUpcomingEvent = 45,
                    timeSensitive = true
                )
            }
        )
        val situation = provider.getTemporalSituation()

        assertEquals("Flight to Mumbai", situation.upcomingEvent)
        assertEquals(45L, situation.minutesUntilUpcomingEvent)
        assertTrue(situation.timeSensitive)
    }

    // 11. Activity inference
    @Test
    fun activityInferenceSpeaking() {
        val engine = NexusSituationActivityEngine()
        val activity = engine.infer(
            app = AppSituation(),
            session = SessionSituation(speaking = true),
            vision = VisionSituation(),
            goal = GoalSituation()
        )
        assertEquals(ActivityMode.SPEAKING, activity.mode)
        assertEquals(0.95f, activity.confidence, 0.01f)
    }

    @Test
    fun activityInferenceNavigation() {
        val engine = NexusSituationActivityEngine()
        val activity = engine.infer(
            app = AppSituation(category = "navigation", foreground = true),
            session = SessionSituation(),
            vision = VisionSituation(),
            goal = GoalSituation()
        )
        assertEquals(ActivityMode.NAVIGATING, activity.mode)
    }

    // 12. Conflict detection
    @Test
    fun conflictDetectionOfflinePendingAction() {
        val engine = NexusSituationConflictEngine()
        val conflicts = engine.detect(
            device = DeviceSituation(),
            app = AppSituation(),
            network = NetworkSituation(connected = false),
            session = SessionSituation(),
            goal = GoalSituation(),
            action = ActionSituation(pending = true),
            temporal = TemporalSituation()
        )

        assertTrue(conflicts.any { it.leftSignal == "Action pending" })
    }

    // 13. Priority scoring
    @Test
    fun priorityScoringForUrgentNotificationsAndAction() {
        val engine = NexusSituationPriorityEngine()
        val priority = engine.calculate(
            device = DeviceSituation(),
            app = AppSituation(),
            network = NetworkSituation(connected = true),
            session = SessionSituation(),
            notifications = NotificationSituation(urgentCount = 1),
            goal = GoalSituation(active = true, blocked = true),
            action = ActionSituation(pending = true, requiresConfirmation = true),
            prediction = PredictionSituation(),
            temporal = TemporalSituation(timeSensitive = true),
            conflicts = emptyList()
        )

        assertTrue(priority == SituationPriority.CRITICAL || priority == SituationPriority.HIGH)
    }

    // 14. Focus selection
    @Test
    fun focusSelectionSelectsHighestUrgency() {
        val engine = NexusSituationFocusEngine()
        val focus = engine.findFocus(
            notifications = NotificationSituation(urgentCount = 2),
            goal = GoalSituation(),
            action = ActionSituation(),
            temporal = TemporalSituation(),
            conflicts = emptyList(),
            app = AppSituation()
        )

        assertNotNull(focus)
        assertEquals("Urgent notification", focus?.title)
        assertEquals(SituationPriority.CRITICAL, focus?.priority)
    }

    // 15. Interruption level
    @Test
    fun interruptionLevelEvaluation() {
        val engine = NexusSituationInterruptionEngine()
        val level = engine.evaluate(
            session = SessionSituation(interruptionAllowed = true),
            notifications = NotificationSituation(urgentCount = 2),
            temporal = TemporalSituation(timeSensitive = true),
            action = ActionSituation(requiresConfirmation = true),
            conflicts = emptyList()
        )

        assertEquals(InterruptionLevel.CRITICAL, level)
    }

    // 16. Situation change detection
    @Test
    fun situationChangeDetectionOnAppChange() {
        val engine = NexusSituationChangeEngine()
        val prev = SituationalContext(app = AppSituation(packageName = "com.google.android.youtube"))
        val curr = SituationalContext(app = AppSituation(packageName = "com.whatsapp"))

        val event = engine.detect(prev, curr)
        assertEquals(SituationChangeType.APP_CHANGED, event.type)
        assertTrue(event.significance >= 0.7f)
    }

    // 17. Privacy sanitization
    @Test
    fun privacySanitizationRedactsRawInformation() {
        val gate = NexusSituationPrivacyGate()
        val raw = SituationalContext(
            notifications = NotificationSituation(
                latestPackage = "com.bank.app",
                unreadCount = 2
            ),
            vision = VisionSituation(
                summary = "A".repeat(500)
            )
        )

        val sanitized = gate.sanitize(raw)
        assertEquals(300, sanitized.vision.summary?.length)
        assertEquals("com.bank.app", sanitized.notifications.latestPackage)
    }

    // 18. Provider failure isolation
    @Test
    fun providerFailureIsolation() = runBlocking {
        val failingDeviceProvider = object : DeviceSituationProvider {
            override suspend fun getDeviceSituation(): DeviceSituation {
                throw RuntimeException("Sensor read timeout")
            }
        }

        val engine = NexusSituationalAwarenessEngine(
            deviceProvider = failingDeviceProvider,
            appProvider = DefaultAppSituationProvider(),
            networkProvider = DefaultNetworkSituationProvider(),
            sessionProvider = DefaultSessionSituationProvider(),
            notificationProvider = DefaultNotificationSituationProvider(),
            activityProvider = DefaultActivitySituationProvider(),
            goalProvider = DefaultGoalSituationProvider(),
            actionProvider = DefaultActionSituationProvider(),
            predictionProvider = DefaultPredictionSituationProvider(),
            visionProvider = DefaultVisionSituationProvider(),
            temporalProvider = DefaultTemporalSituationProvider()
        )

        val context = engine.buildContext()
        assertNotNull(context)
        assertEquals(DeviceSituation(), context.device) // Falls back safely
    }

    // 19. NexusContext integration
    @Test
    fun nexusContextIntegration() = runBlocking {
        val mockRuntime = MockRuntimeContextProvider()
        val mockMemory = MockMemoryResolver()

        val situationEngine = NexusSituationalAwarenessEngine(
            deviceProvider = ContextDrivenDeviceSituationProvider(mockRuntime),
            appProvider = ContextDrivenAppSituationProvider(mockRuntime),
            networkProvider = ContextDrivenNetworkSituationProvider(mockRuntime),
            sessionProvider = DefaultSessionSituationProvider(),
            notificationProvider = DefaultNotificationSituationProvider(),
            activityProvider = DefaultActivitySituationProvider(),
            goalProvider = DefaultGoalSituationProvider(),
            actionProvider = DefaultActionSituationProvider(),
            predictionProvider = DefaultPredictionSituationProvider(),
            visionProvider = DefaultVisionSituationProvider(),
            temporalProvider = DefaultTemporalSituationProvider()
        )

        val adapter = NexusSituationContextAdapter(situationEngine)

        val assembler = NexusContextAssembler(
            runtimeProvider = mockRuntime,
            memoryResolver = mockMemory,
            knowledgeAdapter = null,
            predictionBridge = null,
            situationAdapter = adapter
        )

        val nexusContext = assembler.build(
            conversationId = "conv_1",
            taskId = null,
            input = "kya chal raha hai?",
            channel = InputChannel.TEXT
        )

        assertNotNull(nexusContext.situationalContext)
        assertEquals("com.google.android.apps.maps", nexusContext.situationalContext?.app?.packageName)
        assertEquals(ActivityMode.NAVIGATING, nexusContext.situationalContext?.activity?.mode)
    }
}
