package com.rodrigomachado.riff.core.audio

import android.media.MediaPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaPlayer.InvalidStateBehavior

@RunWith(RobolectricTestRunner::class)
class MediaSongPlayerTest {
    /** Um MediaPlayer que, como no aparelho, falha quando usado depois de liberado. */
    private fun strictPlayer() = MediaPlayer().also {
        shadowOf(
            it,
        ).setInvalidStateBehavior(InvalidStateBehavior.EMULATE)
    }

    @Test
    fun usingThePlayerAfterReleaseIsHarmless() {
        val song = MediaSongPlayer(ApplicationProvider.getApplicationContext(), strictPlayer())

        song.release()

        assertFalse(song.isPlaying)
        assertEquals(0L, song.positionMs)
        song.pause()
        song.play()
        song.release()
    }
}
