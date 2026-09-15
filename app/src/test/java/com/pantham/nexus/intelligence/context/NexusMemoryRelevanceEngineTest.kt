package com.pantham.nexus.intelligence.context

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.EncryptedMemoryWalletRepository
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
class NexusMemoryRelevanceEngineTest {

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
    fun testRelevanceScoringAndFilteringThreshold() = runBlocking {
        val testSource = object : MemorySource {
            override suspend fun getAllMemories(): List<StoredMemory> {
                return listOf(
                    StoredMemory(
                        key = "favorite color",
                        value = "blue sapphire",
                        category = "preferences",
                        importance = 0.8
                    ),
                    StoredMemory(
                        key = "work email",
                        value = "boss@nexus.ai",
                        category = "contact",
                        importance = 0.9
                    ),
                    StoredMemory(
                        key = "irrelevant note",
                        value = "completely unrelated bananas and apples",
                        category = "groceries",
                        importance = 0.1
                    )
                )
            }
        }

        val engine = NexusMemoryRelevanceEngine(testSource)

        // Query matching "favorite color"
        val results = engine.findRelevantMemories(input = "What is my favorite color?", limit = 5)

        assertTrue("Expected at least 1 match", results.isNotEmpty())
        assertEquals("favorite color", results.first().key)
        assertEquals("blue sapphire", results.first().value)
        assertEquals("preferences", results.first().category)
        assertTrue("Relevance must be >= 0.18", results.first().relevance >= 0.18)

        // Ensure irrelevant note was filtered out by >= 0.18 threshold
        val hasIrrelevant = results.any { it.key == "irrelevant note" }
        assertTrue("Irrelevant note should be filtered out", !hasIrrelevant)
    }

    @Test
    fun testRankingDescendingAndLimit() = runBlocking {
        val testSource = object : MemorySource {
            override suspend fun getAllMemories(): List<StoredMemory> {
                return listOf(
                    StoredMemory(key = "project nexus alpha", value = "secret project details", category = "work"),
                    StoredMemory(key = "project nexus beta", value = "nexus secondary details", category = "work"),
                    StoredMemory(key = "project nexus gamma", value = "nexus tertiary details", category = "work"),
                    StoredMemory(key = "project nexus delta", value = "nexus fourth details", category = "work")
                )
            }
        }

        val engine = NexusMemoryRelevanceEngine(testSource)
        val results = engine.findRelevantMemories(input = "tell me about project nexus alpha", limit = 2)

        assertEquals("Limit should cap results to 2", 2, results.size)
        assertTrue("Results should be sorted descending by relevance", results[0].relevance >= results[1].relevance)
        assertEquals("Most relevant should be project nexus alpha", "project nexus alpha", results[0].key)
    }

    @Test
    fun testIntegrationWithEncryptedWalletRepository() = runBlocking {
        // Save items into encrypted wallet & user preferences
        repository.saveMemoryWalletItem(
            title = "WiFi Password Home",
            content = "CyberNexus2026!",
            tag = "CREDENTIALS"
        )
        repository.savePreference(
            key = "user_codename",
            value = "Phantom_Boss",
            category = "PROFILE"
        )

        val memorySource = EncryptedWalletMemorySource(repository)
        val allMemories = memorySource.getAllMemories()
        assertEquals(2, allMemories.size)

        val engine = NexusMemoryRelevanceEngine(memorySource)
        val matched = engine.findRelevantMemories(input = "What is my WiFi password for home?", limit = 5)

        assertEquals(1, matched.size)
        val memory = matched.first()
        assertEquals("WiFi Password Home", memory.key)
        assertEquals("CyberNexus2026!", memory.value)
        assertEquals("CREDENTIALS", memory.category)
        assertTrue(memory.relevance >= 0.18)
    }
}
