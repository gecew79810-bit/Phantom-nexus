package com.pantham.nexus.intelligence.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.action.NexusAction
import com.example.action.NexusActionRouter
import com.example.bridge.AndroidSystemBridge
import com.example.data.local.AppDatabase
import com.example.data.repository.EncryptedMemoryWalletRepository
import com.example.hardware.HardwareController
import com.example.media.MaxMediaManager
import com.pantham.nexus.intelligence.model.ActionStatus
import com.pantham.nexus.intelligence.model.RiskLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IntelligenceActionBridgeTest {

    private lateinit var context: Context
    private lateinit var router: NexusActionRouter
    private lateinit var bridge: IntelligenceActionBridge

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val db = AppDatabase.createInMemoryInstance(context)
        val memoryRepo = EncryptedMemoryWalletRepository(
            conversationDao = db.conversationDao(),
            userPreferenceDao = db.userPreferenceDao(),
            memoryWalletDao = db.memoryWalletDao()
        )
        val testScope = CoroutineScope(Dispatchers.Main)
        val systemBridge = AndroidSystemBridge(context)
        val hardwareController = HardwareController(context, testScope)
        val mediaManager = MaxMediaManager(context)

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

        bridge = IntelligenceActionBridge(router)
    }

    @Test
    fun testHighRiskActionWithoutConfirmationReturnsWaitingForConfirmation() = runBlocking {
        val action = NexusAction.LaunchApp("Settings")
        val result = bridge.execute(action, risk = RiskLevel.HIGH, confirmed = false)

        assertEquals(ActionStatus.WAITING_FOR_CONFIRMATION, result.status)
        assertEquals("Confirmation required.", result.message)
    }

    @Test
    fun testHighRiskActionWithConfirmationExecutes() = runBlocking {
        val action = NexusAction.Volume(direction = com.example.action.VolumeDirection.SET_PERCENT, percent = 50)
        val result = bridge.execute(action, risk = RiskLevel.HIGH, confirmed = true)

        assertEquals(ActionStatus.EXECUTED, result.status)
    }

    @Test
    fun testLowRiskActionExecutesSuccessfully() = runBlocking {
        val action = NexusAction.Volume(direction = com.example.action.VolumeDirection.SET_PERCENT, percent = 80)
        val result = bridge.execute(action, risk = RiskLevel.LOW, confirmed = false)

        assertEquals(ActionStatus.EXECUTED, result.status)
    }
}
