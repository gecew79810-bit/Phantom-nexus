package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * KeyStoreManager:
 * Provides hardware-backed cryptographic passphrases using Android KeyStore (AES-256 GCM).
 * Generates and securely stores the 256-bit encryption key required by SQLCipher for Room.
 */
object KeyStoreManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "PhantomNexus_SQLCipher_Key"
    private const val PREFS_NAME = "phantom_nexus_secure_vault"
    private const val PREF_ENCRYPTED_PASSPHRASE = "enc_db_passphrase"
    private const val PREF_IV = "enc_db_iv"
    private const val GCM_TAG_LENGTH = 128

    @Synchronized
    fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedPassphraseBase64 = prefs.getString(PREF_ENCRYPTED_PASSPHRASE, null)
        val ivBase64 = prefs.getString(PREF_IV, null)

        if (encryptedPassphraseBase64 != null && ivBase64 != null) {
            try {
                val encryptedBytes = Base64.decode(encryptedPassphraseBase64, Base64.NO_WRAP)
                val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                val masterKey = getOrCreateMasterKey()

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
                return cipher.doFinal(encryptedBytes)
            } catch (e: Exception) {
                Log.e("KeyStoreManager", "Failed to decrypt existing SQLCipher passphrase, generating new key", e)
            }
        }

        // Generate a cryptographically secure 32-byte (256-bit) random passphrase
        val secureRandom = SecureRandom()
        val newPassphrase = ByteArray(32)
        secureRandom.nextBytes(newPassphrase)

        try {
            val masterKey = getOrCreateMasterKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, masterKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(newPassphrase)

            prefs.edit()
                .putString(PREF_ENCRYPTED_PASSPHRASE, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                .putString(PREF_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .apply()
        } catch (e: Exception) {
            Log.e("KeyStoreManager", "Failed to encrypt SQLCipher passphrase in Android KeyStore", e)
        }

        return newPassphrase
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
}
