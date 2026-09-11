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

    private var useEncryptedPrefs = true

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            useEncryptedPrefs = true
            EncryptedSharedPreferences.create(
                context,
                "secure_credentials",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            useEncryptedPrefs = false
            context.getSharedPreferences("secure_credentials_fallback", Context.MODE_PRIVATE)
        }
    }

    private val fallbackPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("secure_credentials_fallback", Context.MODE_PRIVATE)
    }

    private val keyAlias = "AutoDeployCredentialKey"
    private val androidKeyStore = "AndroidKeyStore"

    fun saveFtpPassword(key: String, password: String) {
        saveSecureString(key, password)
    }

    fun getFtpPassword(key: String): String? {
        return getSecureString(key)
    }

    fun saveShrotiHostPassword(key: String, password: String) {
        saveSecureString(key, password)
    }

    fun getShrotiHostPassword(key: String): String? {
        return getSecureString(key)
    }

    fun saveGitHubToken(key: String, token: String) {
        saveSecureString(key, token)
    }

    fun getGitHubToken(key: String): String? {
        return getSecureString(key)
    }

    fun deleteCredential(key: String) {
        try {
            prefs.edit().remove(key).apply()
        } catch (ignored: Exception) {}
        try {
            fallbackPrefs.edit().remove(key).apply()
        } catch (ignored: Exception) {}
    }

    fun deletePassword(key: String) = deleteCredential(key)

    private fun saveSecureString(key: String, value: String) {
        if (useEncryptedPrefs) {
            try {
                prefs.edit().putString(key, value).apply()
                return
            } catch (e: Exception) {
                useEncryptedPrefs = false
            }
        }
        // Securely encrypt with Android KeyStore before saving to fallback prefs
        val encrypted = encryptWithKeyStore(value)
        fallbackPrefs.edit().putString(key, encrypted).apply()
    }

    private fun getSecureString(key: String): String? {
        if (useEncryptedPrefs) {
            try {
                val value = prefs.getString(key, null)
                if (value != null) return value
            } catch (e: Exception) {
                useEncryptedPrefs = false
            }
        }
        val fallbackEncrypted = fallbackPrefs.getString(key, null) ?: return null
        return decryptWithKeyStore(fallbackEncrypted)
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
            // Internal fallback: AES with derived key
            try {
                val derivedKey = getDerivedKey()
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.ENCRYPT_MODE, derivedKey)
                val iv = cipher.iv
                val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                val combined = ByteArray(iv.size + cipherText.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
                "ENC:" + Base64.encodeToString(combined, Base64.NO_WRAP)
            } catch (err: Exception) {
                ""
            }
        }
    }

    private fun decryptWithKeyStore(cipherTextBase64: String): String? {
        if (cipherTextBase64.startsWith("ENC:")) {
            return try {
                val raw = cipherTextBase64.removePrefix("ENC:")
                val combined = Base64.decode(raw, Base64.NO_WRAP)
                val iv = combined.copyOfRange(0, 16)
                val cipherText = combined.copyOfRange(16, combined.size)
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, getDerivedKey(), javax.crypto.spec.IvParameterSpec(iv))
                String(cipher.doFinal(cipherText), Charsets.UTF_8)
            } catch (e: Exception) {
                null
            }
        }
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
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getDerivedKey(): javax.crypto.spec.SecretKeySpec {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val seed = (context.packageName + "_AutoDeploy_Salt_Secure_2026").toByteArray(Charsets.UTF_8)
        val keyBytes = digest.digest(seed)
        return javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
    }
}
