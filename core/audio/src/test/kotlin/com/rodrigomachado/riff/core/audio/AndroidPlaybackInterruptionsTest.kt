package com.rodrigomachado.riff.core.audio

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidPlaybackInterruptionsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audio = context.getSystemService(AudioManager::class.java)
    private var interruptions = 0
    private val subject = AndroidPlaybackInterruptions(context)

    private fun unplugHeadphones() {
        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun losingTheAudioFocusInterruptsTheGame() {
        subject.start { interruptions++ }
        val request = shadowOf(audio).lastAudioFocusRequest
        assertNotNull(request)

        request.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) // uma ligação

        assertEquals(1, interruptions)
    }

    @Test
    fun unpluggingTheHeadphonesInterruptsTheGame() {
        subject.start { interruptions++ }

        unplugHeadphones()

        assertEquals(1, interruptions)
    }

    @Test
    fun afterStoppingNothingInterrupts() {
        subject.start { interruptions++ }

        subject.stop()
        unplugHeadphones()

        assertEquals(0, interruptions)
    }

    @Test
    fun aNotificationThatOnlyLowersTheVolumeDoesNotPause() {
        subject.start { interruptions++ }

        shadowOf(audio).lastAudioFocusRequest.listener
            .onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

        assertEquals(0, interruptions)
    }
}
