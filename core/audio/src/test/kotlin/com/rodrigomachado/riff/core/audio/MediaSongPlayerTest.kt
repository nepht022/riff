package com.rodrigomachado.riff.core.audio

import android.media.MediaPlayer
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.ShadowMediaPlayer.InvalidStateBehavior
import org.robolectric.shadows.util.DataSource

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

    @Test
    fun theAppVolumeGoesToThePlayer() {
        val media = MediaPlayer()
        val song = MediaSongPlayer(ApplicationProvider.getApplicationContext(), media)

        song.setVolume(0.4f)

        assertEquals(0.4f, shadowOf(media).leftVolume, 1e-6f)
        assertEquals(0.4f, shadowOf(media).rightVolume, 1e-6f)
    }

    @Test
    fun seekingMovesTheSong() {
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource("musica.ogg"), ShadowMediaPlayer.MediaInfo(60_000, 0))
        val media = MediaPlayer()
        media.setDataSource("musica.ogg")
        media.prepare()
        val song = MediaSongPlayer(ApplicationProvider.getApplicationContext(), media)

        song.seekTo(29_500)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(29_500L, song.positionMs)
    }

    /** MediaPlayer que conta as chamadas: liberar no meio do prepare (em outra thread) quebra o player nativo. */
    private class WatchedPlayer : MediaPlayer() {
        var duringPrepare: () -> Unit = {}
        var preparing = false
        var releasedWhilePreparing = false
        var releases = 0

        override fun prepare() {
            preparing = true
            duringPrepare()
            super.prepare()
            preparing = false
        }

        override fun release() {
            if (preparing) releasedWhilePreparing = true
            releases++
            super.release()
        }
    }

    @Test
    fun leavingWhileTheSongIsStillLoadingReleasesThePlayerOnlyAfterTheLoad() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val uri = "content://riff/musica.ogg"
        ShadowMediaPlayer.addMediaInfo(
            DataSource.toDataSource(context, Uri.parse(uri)),
            ShadowMediaPlayer.MediaInfo(60_000, 0),
        )
        val media = WatchedPlayer()
        val song = MediaSongPlayer(context, media)
        media.duringPrepare = { song.release() }

        runBlocking { song.prepare(uri) }

        assertFalse(media.releasedWhilePreparing)
        assertEquals(1, media.releases)
        assertTrue(!song.isPlaying)
    }
}
