package com.autodeploy.infinityfree.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStorageManager(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "secure_credentials",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback for environments where MasterKey/Tink is constrained
            context.getSharedPreferences("secure_credentials_fallback", Context.MODE_PRIVATE)
        }
    }

    private val keyAlias = "AutoDeployCredentialKey"
    private val androidKeyStore = "AndroidKeyStore"

    fun saveFtpPassword(key: String, password: String) {
        try {
            prefs.edit().putString(key, password).apply()
        } catch (e: Exception) {
            // Use custom KeyStore cipher fallback
            val encrypted = encryptWithKeyStore(password)
            context.getSharedPreferences("secure_credentials_fallback", Context.MODE_PRIVATE)
                .edit()
                .putString(key, encrypted)
                .apply()
        }
    }

    fun getFtpPassword(key: String): String? {
        return try {
            val value = prefs.getString(key, null)
            if (value == null) {
                val fallbackValue = context.getSharedPreferences("secure_credentials_fallback", Context.MODE_PRIVATE)
                    .getString(key, null)
                fallbackValue?.let { decryptWithKeyStore(it) }
            } else {
                value
            }
        } catch (e: Exception) {
            val fallbackValue = context.getSharedPreferences("secure_credentials_fallback", Context.MODE_PRIVATE)
                .getString(key, null)
            fallbackValue?.let { decryptWithKeyStore(it) }
        }
    }

    fun deletePassword(key: String) {
        prefs.edit().remove(key).apply()
        context.getSharedPreferences("secure_credentials_fallback", Context.MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(androidKeyStore).apply { load(null) }
        if (keyStore.containsAlias(keyAlias)) {
            val entry = keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance("AES", androidKeyStore)
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            keyAlias,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
        .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encryptWithKeyStore(plainText: String): String {
        return try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Absolute fallback (Base64 obfuscation for testing/mock environments without hardware Keystore)
            Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    private fun decryptWithKeyStore(cipherTextBase64: String): String? {
        return try {
            val combined = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
            val keyStore = KeyStore.getInstance(androidKeyStore).apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) {
                val secretKey = (keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
                val iv = combined.copyOfRange(0, 12)
                val cipherText = combined.copyOfRange(12, combined.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                String(cipher.doFinal(cipherText), Charsets.UTF_8)
            } else {
                String(combined, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            try {
                String(Base64.decode(cipherTextBase64, Base64.NO_WRAP), Charsets.UTF_8)
            } catch (ignored: Exception) {
                null
            }
        }
    }
}
