package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiEngineMode
import com.example.ai.ConsoleMessage
import com.example.data.local.AppDatabase
import com.example.data.local.ConversationMessageEntity
import com.example.data.local.MemoryWalletItemEntity
import com.example.data.local.UserPreferenceEntity
import com.example.data.repository.EncryptedMemoryWalletRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EncryptedStorageUnitTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: EncryptedMemoryWalletRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemoryInstance(context)
        repository = EncryptedMemoryWalletRepository(
            conversationDao = database.conversationDao(),
            userPreferenceDao = database.userPreferenceDao(),
            memoryWalletDao = database.memoryWalletDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testConversationMessageEntity() {
        val entity = ConversationMessageEntity(
            id = "test_msg_1",
            sender = "USER",
            text = "Hello MAX, remember my password",
            timestamp = "10:30 AM",
            isVoice = false
        )
        assertEquals("test_msg_1", entity.id)
        assertEquals("USER", entity.sender)
        assertEquals("Hello MAX, remember my password", entity.text)
        assertTrue(entity.createdAt > 0)
    }

    @Test
    fun testUserPreferenceEntity() {
        val pref = UserPreferenceEntity(
            key = "ai_engine_mode",
            value = AiEngineMode.OLLAMA_OFFLINE.name,
            category = "AI_ENGINE"
        )
        assertEquals("ai_engine_mode", pref.key)
        assertEquals("OLLAMA_OFFLINE", pref.value)
        assertEquals("AI_ENGINE", pref.category)
    }

    @Test
    fun testMemoryWalletItemEntity() {
        val item = MemoryWalletItemEntity(
            id = "MEM_123",
            title = "WiFi Credential",
            content = "NexusCore5G:Pass1234",
            tag = "WALLET",
            isEncrypted = true
        )
        assertEquals("MEM_123", item.id)
        assertEquals("WiFi Credential", item.title)
        assertTrue(item.isEncrypted)
        assertEquals("WALLET", item.tag)
    }

    @Test
    fun testRoomChatHistoryPersistenceAndRetrieval() = runBlocking {
        val msg1 = ConsoleMessage(
            id = "msg_1",
            sender = "USER",
            text = "What is the weather today?",
            timestamp = "09:00 AM",
            isVoice = false
        )
        val msg2 = ConsoleMessage(
            id = "msg_2",
            sender = "MAX",
            text = "It is sunny and 28°C.",
            timestamp = "09:01 AM",
            isVoice = true
        )

        repository.saveMessage(msg1)
        repository.saveMessage(msg2)

        val messages = repository.allMessages.first()
        assertEquals(2, messages.size)
        assertEquals("msg_1", messages[0].id)
        assertEquals("What is the weather today?", messages[0].text)
        assertEquals("msg_2", messages[1].id)
        assertTrue(messages[1].isVoice)

        val totalCount = repository.messageCount.first()
        assertEquals(2, totalCount)

        val voiceCount = repository.voiceMessageCount.first()
        assertEquals(1, voiceCount)
    }

    @Test
    fun testRoomUserPreferencesPersistenceAndRetrieval() = runBlocking {
        repository.savePreference("ai_engine_mode", "GEMINI_CLOUD", "AI_ENGINE")
        repository.savePreference("is_playful_mode", "true", "AI_ENGINE")
        repository.savePreference("assistant_language", "HINDI", "GENERAL")

        val engineMode = repository.getPreference("ai_engine_mode")
        assertEquals("GEMINI_CLOUD", engineMode)

        val playful = repository.getBooleanPreferenceFlow("is_playful_mode", false).first()
        assertTrue(playful)

        val allPrefs = repository.allPreferences.first()
        assertEquals(3, allPrefs.size)

        val aiPrefs = repository.getPreferencesByCategory("AI_ENGINE").first()
        assertEquals(2, aiPrefs.size)

        repository.deletePreference("is_playful_mode")
        val updatedPrefs = repository.allPreferences.first()
        assertEquals(2, updatedPrefs.size)
    }

    @Test
    fun testRoomChatSearchAndDelete() = runBlocking {
        repository.saveMessage(
            ConsoleMessage(id = "search_1", sender = "USER", text = "Turn on flashlight", timestamp = "10:00 AM")
        )
        repository.saveMessage(
            ConsoleMessage(id = "search_2", sender = "USER", text = "Play music", timestamp = "10:05 AM")
        )

        val searchResults = repository.searchMessages("flashlight").first()
        assertEquals(1, searchResults.size)
        assertEquals("search_1", searchResults[0].id)

        repository.deleteMessage("search_1")
        val remaining = repository.allMessages.first()
        assertEquals(1, remaining.size)
        assertEquals("search_2", remaining[0].id)

        repository.clearHistory()
        val emptyList = repository.allMessages.first()
        assertTrue(emptyList.isEmpty())
    }
}
