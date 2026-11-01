package com.rodrigomachado.riff.core.audio

import android.media.MediaPlayer
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
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
    private val noPackages = RandomReadOpener { error("sem .sng neste teste") }

    /** Um MediaPlayer que, como no aparelho, falha quando usado depois de liberado. */
    private fun strictPlayer() = MediaPlayer().also {
        shadowOf(
            it,
        ).setInvalidStateBehavior(InvalidStateBehavior.EMULATE)
    }

    @Test
    fun usingThePlayerAfterReleaseIsHarmless() {
        val song = MediaSongPlayer(ApplicationProvider.getApplicationContext(), noPackages, strictPlayer())

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
        val song = MediaSongPlayer(ApplicationProvider.getApplicationContext(), noPackages, media)

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
        val song = MediaSongPlayer(ApplicationProvider.getApplicationContext(), noPackages, media)

        song.seekTo(29_500)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(29_500L, song.positionMs)
    }

    /**
     * Registro no S25: o MediaPlayer diz "tocando" em 0 ms antes de o som sair; a pista andava sozinha e meio segundo
     * depois voltava 155 ms. Até a posição andar, o player está começando (a pista espera, como nas faixas separadas).
     */
    @Test
    fun theStartWaitsUntilThePositionMoves() {
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource("inicio.ogg"), ShadowMediaPlayer.MediaInfo(60_000, 0))
        val media = MediaPlayer()
        media.setDataSource("inicio.ogg")
        media.prepare()
        val song = MediaSongPlayer(ApplicationProvider.getApplicationContext(), noPackages, media)

        song.play()
        assertTrue(song.isStarting)

        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200))
        assertTrue(song.positionMs > 0)
        assertFalse(song.isStarting)
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
        val song = MediaSongPlayer(context, noPackages, media)
        media.duringPrepare = { song.release() }

        runBlocking { song.prepare(uri) }

        assertFalse(media.releasedWhilePreparing)
        assertEquals(1, media.releases)
        assertTrue(!song.isPlaying)
    }
}
