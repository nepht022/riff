package com.rodrigomachado.riff

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenLibraryTest {
    @Test
    fun theSeparationNotificationOpensTheLibrary() {
        assertTrue(opensLibrary(Intent().putExtra("riff.abrir_biblioteca", true)))
        assertFalse(opensLibrary(Intent()))
        assertFalse(opensLibrary(null))
    }

    @Test
    fun theRequestIsUsedOnlyOnceSoRotatingTheScreenDoesNotOpenTheLibraryAgain() {
        val intent = Intent().putExtra("riff.abrir_biblioteca", true)

        assertTrue(consumeOpenLibrary(intent))
        assertFalse(consumeOpenLibrary(intent))
    }
}
