package com.replysaathi.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Personal API key encrypted with a non-exportable Android Keystore key. */
class KeyVault(context: Context) {
    private val prefs = context.getSharedPreferences("gemini_vault", Context.MODE_PRIVATE)
    private val alias = "reply_bot_personal_gemini_v1"
    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("encrypted", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) { "Cannot save key" }
    }
    fun read(): String {
        val encoded = prefs.getString("encrypted", null) ?: return ""
        val iv = prefs.getString("iv", null) ?: return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        return String(cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), Charsets.UTF_8)
    }
    fun clear() { prefs.edit().clear().commit() }
}
