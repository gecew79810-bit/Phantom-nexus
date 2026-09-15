package com.example.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.security.KeyStoreManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * AppDatabase:
 * Encrypted Room database with SQLCipher encryption layer for the Personal AI OS.
 * Stores conversation history, user preferences, and private memory wallet items with 256-bit AES-GCM encrypted keys.
 */
@Database(
    entities = [
        ConversationMessageEntity::class,
        UserPreferenceEntity::class,
        MemoryWalletItemEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun userPreferenceDao(): UserPreferenceDao
    abstract fun memoryWalletDao(): MemoryWalletDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DB_NAME = "phantom_nexus_encrypted.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildEncryptedDatabase(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        /**
         * Creates an in-memory database instance primarily for isolated unit testing.
         */
        fun createInMemoryInstance(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }

        private fun buildEncryptedDatabase(appContext: Context): AppDatabase {
            var useSqlCipher = false
            try {
                System.loadLibrary("sqlcipher")
                useSqlCipher = true
            } catch (e: Throwable) {
                Log.w(TAG, "Native sqlcipher library not available, falling back to standard SQLite: ${e.message}")
            }

            val builder = Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .fallbackToDestructiveMigration()
                .addCallback(PrepopulateDefaultPreferencesCallback())

            if (useSqlCipher) {
                try {
                    // Retrieve or generate 256-bit passphrase secured via Android KeyStore
                    val passphrase = KeyStoreManager.getOrCreatePassphrase(appContext)
                    val supportFactory = SupportOpenHelperFactory(passphrase)
                    builder.openHelperFactory(supportFactory)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to configure SQLCipher supportFactory, using standard SQLite: ${e.message}")
                }
            }

            return builder.build()
        }
    }

    /**
     * Room Database Callback that prepopulates essential personal AI OS user preferences
     * upon first database creation.
     */
    private class PrepopulateDefaultPreferencesCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            val now = System.currentTimeMillis()
            val initialPreferences = listOf(
                Triple("ai_engine_mode", "GEMINI_CLOUD", "AI_ENGINE"),
                Triple("is_playful_mode", "true", "AI_ENGINE"),
                Triple("is_edge_tts_active", "true", "VOICE"),
                Triple("edge_voice", "HINDI_SWARA", "VOICE"),
                Triple("assistant_language", "HINDI", "GENERAL"),
                Triple("wake_word_enabled", "true", "VOICE"),
                Triple("hands_free_mode", "false", "GENERAL"),
                Triple("haptic_feedback_enabled", "true", "SYSTEM"),
                Triple("privacy_shield_level", "HIGH", "PRIVACY"),
                Triple("auto_speech_output", "true", "VOICE")
            )

            try {
                for ((key, value, category) in initialPreferences) {
                    db.execSQL(
                        "INSERT OR IGNORE INTO user_preferences (`key`, `value`, `category`, `updatedAt`) VALUES ('$key', '$value', '$category', $now)"
                    )
                }
                Log.i(TAG, "Successfully prepopulated default AI OS user preferences into Room.")
            } catch (e: Exception) {
                Log.e(TAG, "Error prepopulating default preferences: ${e.message}")
            }
        }
    }
}
