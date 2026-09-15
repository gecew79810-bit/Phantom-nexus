package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversation_messages ORDER BY createdAt ASC")
    fun getAllMessages(): Flow<List<ConversationMessageEntity>>

    @Query("SELECT * FROM conversation_messages ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ConversationMessageEntity>

    @Query("SELECT * FROM conversation_messages ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    fun getPagedMessages(limit: Int, offset: Int): Flow<List<ConversationMessageEntity>>

    @Query("SELECT * FROM conversation_messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): ConversationMessageEntity?

    @Query("SELECT * FROM conversation_messages WHERE text LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchMessages(query: String): Flow<List<ConversationMessageEntity>>

    @Query("SELECT * FROM conversation_messages WHERE sender = :sender ORDER BY createdAt ASC")
    fun getMessagesBySender(sender: String): Flow<List<ConversationMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ConversationMessageEntity>)

    @Query("DELETE FROM conversation_messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    @Query("DELETE FROM conversation_messages")
    suspend fun clearAllMessages()

    @Query("SELECT COUNT(*) FROM conversation_messages")
    fun getMessageCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM conversation_messages WHERE isVoice = 1")
    fun getVoiceMessageCount(): Flow<Int>
}

@Dao
interface UserPreferenceDao {

    @Query("SELECT * FROM user_preferences ORDER BY `key` ASC")
    fun getAllPreferences(): Flow<List<UserPreferenceEntity>>

    @Query("SELECT * FROM user_preferences ORDER BY `key` ASC")
    suspend fun getAllPreferencesList(): List<UserPreferenceEntity>

    @Query("SELECT * FROM user_preferences WHERE category = :category ORDER BY `key` ASC")
    fun getPreferencesByCategory(category: String): Flow<List<UserPreferenceEntity>>

    @Query("SELECT value FROM user_preferences WHERE `key` = :key LIMIT 1")
    suspend fun getPreference(key: String): String?

    @Query("SELECT value FROM user_preferences WHERE `key` = :key LIMIT 1")
    fun getPreferenceFlow(key: String): Flow<String?>

    @Query("SELECT * FROM user_preferences WHERE `key` = :key LIMIT 1")
    suspend fun getPreferenceEntity(key: String): UserPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreference(preference: UserPreferenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreferences(preferences: List<UserPreferenceEntity>)

    @Query("DELETE FROM user_preferences WHERE `key` = :key")
    suspend fun deletePreference(key: String)

    @Query("DELETE FROM user_preferences")
    suspend fun clearAllPreferences()

    @Query("SELECT COUNT(*) FROM user_preferences")
    fun getPreferenceCount(): Flow<Int>
}

@Dao
interface MemoryWalletDao {

    @Query("SELECT * FROM memory_wallet_items ORDER BY timestamp DESC")
    fun getAllWalletItems(): Flow<List<MemoryWalletItemEntity>>

    @Query("SELECT * FROM memory_wallet_items ORDER BY timestamp DESC")
    suspend fun getAllWalletItemsList(): List<MemoryWalletItemEntity>

    @Query("SELECT * FROM memory_wallet_items WHERE tag = :tag ORDER BY timestamp DESC")
    fun getWalletItemsByTag(tag: String): Flow<List<MemoryWalletItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWalletItem(item: MemoryWalletItemEntity)

    @Query("DELETE FROM memory_wallet_items WHERE id = :id")
    suspend fun deleteWalletItem(id: String)

    @Query("DELETE FROM memory_wallet_items")
    suspend fun clearWallet()

    @Query("SELECT COUNT(*) FROM memory_wallet_items")
    fun getWalletCount(): Flow<Int>
}
