package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.action.MemoryOpType
import com.example.action.NexusAction
import com.example.action.NexusActionRouter
import com.example.ai.dialogue.MultiTurnSessionManager
import com.example.bridge.AndroidSystemBridge
import com.example.data.local.AppDatabase
import com.example.data.repository.EncryptedMemoryWalletRepository
import com.example.hardware.HardwareController
import com.example.media.MaxMediaManager
import com.example.security.ToolPermissionMatrix
import com.example.voice.AssistantLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NexusIntegrationTest {

    private lateinit var context: Context
    private lateinit var multiTurnManager: MultiTurnSessionManager
    private lateinit var router: NexusActionRouter
    private lateinit var memoryRepo: EncryptedMemoryWalletRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        val db = AppDatabase.createInMemoryInstance(context)
        memoryRepo = EncryptedMemoryWalletRepository(
            conversationDao = db.conversationDao(),
            userPreferenceDao = db.userPreferenceDao(),
            memoryWalletDao = db.memoryWalletDao()
        )

        val testScope = CoroutineScope(Dispatchers.Main)
        val systemBridge = AndroidSystemBridge(context)
        val hardwareController = HardwareController(context, testScope)
        val mediaManager = MaxMediaManager(context)
        val contextEngine = com.example.context.NexusContextEngine(context, hardwareController, mediaManager)
        multiTurnManager = MultiTurnSessionManager(contextEngine)

        router = NexusActionRouter(
            context = context,
            systemBridge = systemBridge,
            hardwareController = hardwareController,
            mediaManager = mediaManager,
            memoryRepository = memoryRepo,
            callManager = null,
            ttsProvider = null,
            onTaskUpdated = {},
            onRequestConfirmation = {},
            coroutineScope = testScope
        )
    }

    @Test
    fun testMultiTurnIncompleteCommandDetection() {
        val incomplete = multiTurnManager.checkIncompleteCommand(
            "Rahul ko message bhejo",
            AssistantLanguage.HINDI
        )
        assertNotNull(incomplete)
        assertTrue(incomplete!!.handled)
        assertTrue(incomplete.responseSpeech.contains("संदेश") || incomplete.responseSpeech.contains("मैसेज"))
    }

    @Test
    fun testMultiTurnFollowUpSlotFillingAndConfirmation() {
        multiTurnManager.checkIncompleteCommand("Send message to Rahul", AssistantLanguage.ENGLISH)
        
        val followUp = multiTurnManager.processFollowUp("I will be 15 minutes late", AssistantLanguage.ENGLISH)
        assertTrue(followUp.handled)
        assertTrue(followUp.responseSpeech.contains("Send", ignoreCase = true))

        val confirmed = multiTurnManager.processFollowUp("yes please", AssistantLanguage.ENGLISH)
        assertTrue(confirmed.handled)
        assertNotNull(confirmed.readyAction)
        assertTrue(confirmed.readyAction is NexusAction.Sms)
        val smsAction = confirmed.readyAction as NexusAction.Sms
        assertEquals("Rahul", smsAction.phoneNumber)
        assertEquals("I will be 15 minutes late", smsAction.message)
    }

    @Test
    fun testToolPermissionMatrixProfileMapping() {
        val matrix = ToolPermissionMatrix.getInstance(context)
        val launchProfile = matrix.getProfileForAction(NexusAction.LaunchApp("com.whatsapp"))
        assertEquals("LAUNCH_APP", launchProfile.toolName)

        val smsProfile = matrix.getProfileForAction(NexusAction.Sms("9999999999", "Hello"))
        assertEquals("SEND_SMS", smsProfile.toolName)
        assertTrue(matrix.requiresConfirmation(NexusAction.Sms("9999999999", "Hello")))
    }

    @Test
    fun testActionRouterMemoryOperationVerification() = runBlocking {
        val writeAction = NexusAction.Memory(
            opType = MemoryOpType.REMEMBER,
            key = "pref_coffee",
            value = "Espresso with oat milk"
        )
        val res = router.routeAction(writeAction, bypassConfirmation = true, language = AssistantLanguage.ENGLISH)
        assertTrue(res.success)

        val pref = memoryRepo.getPreference("pref_coffee")
        assertEquals("Espresso with oat milk", pref)
    }
}
