package com.rodrigomachado.riff.core.spotify.web

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** O refresh token da API Web do Spotify: um segredo do usuário, guardado só criptografado. */
interface TokenStore {
    fun read(): String?

    fun write(refreshToken: String?)
}

fun interface SecretKeyProvider {
    fun key(): SecretKey
}

/** AES/GCM com a chave dada; nas preferências fica só o texto cifrado (com o IV na frente). */
class EncryptedTokenStore(private val prefs: SharedPreferences, private val keys: SecretKeyProvider) : TokenStore {
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // ilegível (chave trocada): como se não houvesse
    override fun read(): String? {
        val stored = prefs.getString(KEY, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keys.key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    override fun write(refreshToken: String?) {
        if (refreshToken == null) {
            prefs.edit().remove(KEY).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keys.key())
        val sealed = cipher.iv + cipher.doFinal(refreshToken.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }

    private companion object {
        const val KEY = "refresh"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/** A chave fica no Android Keystore: não sai do aparelho nem aparece nos arquivos do app. */
class AndroidKeystoreKey(private val alias: String) : SecretKeyProvider {
    override fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_BITS = 256
    }
}
