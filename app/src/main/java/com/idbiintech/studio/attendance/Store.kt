package com.idbiintech.studio.attendance

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * App settings. The login token is encrypted with a key kept in the Android Keystore,
 * so it cannot be copied to another phone.
 */
class Store(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("studio_attendance", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString(KEY_SERVER, null)?.takeIf { it.isNotBlank() } ?: BuildConfig.STUDIO_URL
        set(value) = sp.edit().putString(KEY_SERVER, value).apply()

    /** Random id created once per installation. Sent with every request. */
    val deviceId: String
        get() {
            val existing = sp.getString(KEY_DEVICE, null)
            if (!existing.isNullOrBlank()) return existing
            val id = UUID.randomUUID().toString()
            sp.edit().putString(KEY_DEVICE, id).apply()
            return id
        }

    var token: String?
        get() = sp.getString(KEY_TOKEN, null)?.let { decrypt(it) }
        set(value) {
            if (value == null) sp.edit().remove(KEY_TOKEN).apply()
            else sp.edit().putString(KEY_TOKEN, encrypt(value)).apply()
        }

    var employeeName: String
        get() = sp.getString(KEY_NAME, "") ?: ""
        set(value) = sp.edit().putString(KEY_NAME, value).apply()

    var employeeCode: String
        get() = sp.getString(KEY_CODE, "") ?: ""
        set(value) = sp.edit().putString(KEY_CODE, value).apply()

    val isActivated: Boolean
        get() = !token.isNullOrEmpty()

    fun clearSession() {
        sp.edit().remove(KEY_TOKEN).remove(KEY_NAME).remove(KEY_CODE).apply()
    }

    // ---------------------------------------------------------------- crypto

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        val entry = ks.getEntry(KEY_ALIAS, null)
        if (entry is KeyStore.SecretKeyEntry) return entry.secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }

    private fun decrypt(stored: String): String? = try {
        val parts = stored.split(":")
        if (parts.size != 2) {
            null
        } else {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val KEY_SERVER = "server_url"
        private const val KEY_DEVICE = "device_id"
        private const val KEY_TOKEN = "token"
        private const val KEY_NAME = "emp_name"
        private const val KEY_CODE = "emp_code"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "studio_attendance_token"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
