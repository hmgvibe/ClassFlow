package com.ray.classflow.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.google.gson.Gson
import com.ray.classflow.model.Account
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences("classflow_secure", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun read(): Account? = readEncrypted(KEY_ACCOUNT, Account::class.java)

    fun write(account: Account) = writeEncrypted(KEY_ACCOUNT, account)

    fun readPendingLogin(): LoginSession? = readEncrypted(KEY_PENDING_LOGIN, LoginSession::class.java)

    fun writePendingLogin(session: LoginSession) = writeEncrypted(KEY_PENDING_LOGIN, session)

    fun clearPendingLogin() {
        preferences.edit { remove(KEY_PENDING_LOGIN) }
    }

    fun clear() {
        preferences.edit {
            remove(KEY_ACCOUNT)
            remove(KEY_PENDING_LOGIN)
        }
    }

    private fun <T> readEncrypted(preferenceKey: String, type: Class<T>): T? {
        val encoded = preferences.getString(preferenceKey, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val ivLength = bytes.first().toInt()
            val iv = bytes.copyOfRange(1, ivLength + 1)
            val ciphertext = bytes.copyOfRange(ivLength + 1, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            gson.fromJson(String(cipher.doFinal(ciphertext), Charsets.UTF_8), type)
        }.getOrElse {
            preferences.edit { remove(preferenceKey) }
            null
        }
    }

    private fun writeEncrypted(preferenceKey: String, value: Any) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(gson.toJson(value).toByteArray(Charsets.UTF_8))
        val combined = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + encrypted
        preferences.edit { putString(preferenceKey, Base64.encodeToString(combined, Base64.NO_WRAP)) }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ACCOUNT = "account"
        const val KEY_PENDING_LOGIN = "pending_login"
        const val KEY_ALIAS = "classflow_account_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

