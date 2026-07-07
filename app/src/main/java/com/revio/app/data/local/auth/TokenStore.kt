package com.revio.app.data.local.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun read(): AuthTokens? = synchronized(lock) {
        runCatching {
            val encrypted = preferences.getString(KEY_PAYLOAD, null) ?: return null
            val parts = encrypted.split(':', limit = 2)
            if (parts.size != 2) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
            )
            val plaintext = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
                .toString(Charsets.UTF_8)
            val separator = plaintext.indexOf('\n')
            if (separator <= 0 || separator == plaintext.lastIndex) return null
            AuthTokens(
                accessToken = plaintext.substring(0, separator),
                refreshToken = plaintext.substring(separator + 1),
            )
        }.getOrElse {
            clear()
            null
        }
    }

    fun save(tokens: AuthTokens) {
        synchronized(lock) {
            require(tokens.accessToken.isNotBlank() && tokens.refreshToken.isNotBlank())
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val plaintext = "${tokens.accessToken}\n${tokens.refreshToken}".toByteArray()
            val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(cipher.doFinal(plaintext), Base64.NO_WRAP)
            preferences.edit().putString(KEY_PAYLOAD, payload).commit()
        }
    }

    fun clear() {
        synchronized(lock) {
            preferences.edit().remove(KEY_PAYLOAD).commit()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val FILE_NAME = "secure_auth_tokens"
        const val KEY_PAYLOAD = "token_pair"
        const val KEY_ALIAS = "revio_auth_tokens"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
