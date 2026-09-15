package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Encrypted Conversation Message Entity stored in Room Database.
 * Stores chat interactions between user and MAX assistant with indexing on timestamp & sender.
 */
@Entity(
    tableName = "conversation_messages",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["sender"])
    ]
)
data class ConversationMessageEntity(
    @PrimaryKey
    val id: String,
    val sender: String, // "USER", "MAX", "SYSTEM"
    val text: String,
    val timestamp: String,
    val isVoice: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val messageType: String = "TEXT" // "TEXT", "VOICE", "VISION", "SYSTEM"
)

/**
 * User Preference Entity stored in Room Database.
 * Stores core system configurations, AI engine parameters, voice settings, and personalization options.
 */
@Entity(
    tableName = "user_preferences",
    indices = [
        Index(value = ["category"])
    ]
)
data class UserPreferenceEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val category: String = "GENERAL", // "GENERAL", "AI_ENGINE", "VOICE", "PRIVACY", "SYSTEM"
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Memory Wallet Item Entity for Phantom Nexus long-term memories and notes.
 */
@Entity(
    tableName = "memory_wallet_items",
    indices = [
        Index(value = ["tag"]),
        Index(value = ["timestamp"])
    ]
)
data class MemoryWalletItemEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val content: String,
    val tag: String = "WALLET",
    val isEncrypted: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)
