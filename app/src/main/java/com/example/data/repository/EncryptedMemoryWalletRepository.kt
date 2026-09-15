package com.example.data.repository

import com.example.ai.ConsoleMessage
import com.example.data.local.ConversationDao
import com.example.data.local.ConversationMessageEntity
import com.example.data.local.MemoryWalletDao
import com.example.data.local.MemoryWalletItemEntity
import com.example.data.local.UserPreferenceDao
import com.example.data.local.UserPreferenceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * EncryptedMemoryWalletRepository:
 * Repository layer abstracting encrypted Room operations for:
 * - Real-time chat history persistence and reactive streaming
 * - User preferences & AI OS engine configurations
 * - Phantom Nexus Memory Wallet private items & notes
 */
class EncryptedMemoryWalletRepository(
    private val conversationDao: ConversationDao,
    private val userPreferenceDao: UserPreferenceDao,
    private val memoryWalletDao: MemoryWalletDao
) {

    // ==========================================
    // --- 1. CHAT HISTORY (Room Persistence) ---
    // ==========================================

    val allMessages: Flow<List<ConsoleMessage>> = conversationDao.getAllMessages().map { entities ->
        entities.map { it.toConsoleMessage() }
    }

    val messageCount: Flow<Int> = conversationDao.getMessageCount()

    val voiceMessageCount: Flow<Int> = conversationDao.getVoiceMessageCount()

    suspend fun saveMessage(message: ConsoleMessage, messageType: String = "TEXT") {
        conversationDao.insertMessage(
            ConversationMessageEntity(
                id = message.id,
                sender = message.sender,
                text = message.text,
                timestamp = message.timestamp,
                isVoice = message.isVoice,
                createdAt = System.currentTimeMillis(),
                messageType = messageType
            )
        )
    }

    suspend fun saveMessages(messages: List<ConsoleMessage>) {
        val entities = messages.map { msg ->
            ConversationMessageEntity(
                id = msg.id,
                sender = msg.sender,
                text = msg.text,
                timestamp = msg.timestamp,
                isVoice = msg.isVoice,
                createdAt = System.currentTimeMillis()
            )
        }
        conversationDao.insertMessages(entities)
    }

    suspend fun getRecentMessages(limit: Int): List<ConsoleMessage> {
        return conversationDao.getRecentMessages(limit).map { it.toConsoleMessage() }
    }

    fun searchMessages(query: String): Flow<List<ConsoleMessage>> {
        return conversationDao.searchMessages(query).map { entities ->
            entities.map { it.toConsoleMessage() }
        }
    }

    suspend fun deleteMessage(id: String) {
        conversationDao.deleteMessageById(id)
    }

    suspend fun clearHistory() {
        conversationDao.clearAllMessages()
    }

    // ============================================
    // --- 2. USER PREFERENCES (Room Persistence) ---
    // ============================================

    val allPreferences: Flow<List<UserPreferenceEntity>> = userPreferenceDao.getAllPreferences()

    val preferenceCount: Flow<Int> = userPreferenceDao.getPreferenceCount()

    fun getPreferencesByCategory(category: String): Flow<List<UserPreferenceEntity>> {
        return userPreferenceDao.getPreferencesByCategory(category)
    }

    suspend fun getPreference(key: String): String? = userPreferenceDao.getPreference(key)

    fun getPreferenceFlow(key: String): Flow<String?> = userPreferenceDao.getPreferenceFlow(key)

    fun getPreferenceFlow(key: String, defaultValue: String): Flow<String> {
        return userPreferenceDao.getPreferenceFlow(key).map { it ?: defaultValue }
    }

    fun getBooleanPreferenceFlow(key: String, defaultValue: Boolean): Flow<Boolean> {
        return userPreferenceDao.getPreferenceFlow(key).map { it?.toBooleanStrictOrNull() ?: defaultValue }
    }

    suspend fun savePreference(key: String, value: String, category: String = "GENERAL") {
        userPreferenceDao.setPreference(
            UserPreferenceEntity(
                key = key,
                value = value,
                category = category,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveBooleanPreference(key: String, value: Boolean, category: String = "GENERAL") {
        savePreference(key, value.toString(), category)
    }

    suspend fun deletePreference(key: String) {
        userPreferenceDao.deletePreference(key)
    }

    suspend fun getAllPreferencesList(): List<UserPreferenceEntity> {
        return userPreferenceDao.getAllPreferencesList()
    }

    suspend fun clearPreferences() {
        userPreferenceDao.clearAllPreferences()
    }

    // ==============================================
    // --- 3. MEMORY WALLET ITEMS (Room Vault) ------
    // ==============================================

    val walletItems: Flow<List<MemoryWalletItemEntity>> = memoryWalletDao.getAllWalletItems()

    suspend fun getAllWalletItemsList(): List<MemoryWalletItemEntity> {
        return memoryWalletDao.getAllWalletItemsList()
    }

    val walletCount: Flow<Int> = memoryWalletDao.getWalletCount()

    fun getWalletItemsByTag(tag: String): Flow<List<MemoryWalletItemEntity>> {
        return memoryWalletDao.getWalletItemsByTag(tag)
    }

    suspend fun saveMemoryWalletItem(title: String, content: String, tag: String = "WALLET") {
        val id = "MEM_" + System.currentTimeMillis()
        memoryWalletDao.insertWalletItem(
            MemoryWalletItemEntity(
                id = id,
                title = title,
                content = content,
                tag = tag,
                isEncrypted = true,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteMemoryWalletItem(id: String) {
        memoryWalletDao.deleteWalletItem(id)
    }

    suspend fun clearMemoryWallet() {
        memoryWalletDao.clearWallet()
    }

    suspend fun clearAllMemoryWallet() {
        clearMemoryWallet()
    }

    // Extension mapper
    private fun ConversationMessageEntity.toConsoleMessage(): ConsoleMessage {
        return ConsoleMessage(
            id = this.id,
            sender = this.sender,
            text = this.text,
            timestamp = this.timestamp,
            isVoice = this.isVoice
        )
    }
}
