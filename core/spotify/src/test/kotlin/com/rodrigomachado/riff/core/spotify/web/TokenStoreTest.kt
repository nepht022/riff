package com.rodrigomachado.riff.core.spotify.web

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
class TokenStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val store = EncryptedTokenStore(
        context.getSharedPreferences("riff_spotify_web", Context.MODE_PRIVATE),
    ) { key }

    @Test
    fun theTokenIsNotStoredAsPlainText() {
        store.write("refresh-secreto-123")

        val raw = context.getSharedPreferences("riff_spotify_web", Context.MODE_PRIVATE).all.values.joinToString()
        assertFalse(raw.contains("refresh-secreto-123"))
        assertEquals("refresh-secreto-123", store.read())
    }

    @Test
    fun writingNothingErasesIt() {
        store.write("refresh")
        store.write(null)

        assertNull(store.read())
    }
}
