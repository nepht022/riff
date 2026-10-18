package com.rodrigomachado.riff.core.spotify.session

import android.media.session.PlaybackState.STATE_BUFFERING
import android.media.session.PlaybackState.STATE_PAUSED
import android.media.session.PlaybackState.STATE_PLAYING
import android.media.session.PlaybackState.STATE_STOPPED
import com.rodrigomachado.riff.core.model.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSnapshotTest {
    private fun snap(
        mediaId: String? = "spotify:track:1",
        state: Int = STATE_PLAYING,
        positionMs: Long = 10_000,
        updatedAtElapsedMs: Long = 500,
        isAd: Boolean = false,
    ) = SessionSnapshot(mediaId, "T", "A", 200_000, state, positionMs, updatedAtElapsedMs, 1f, isAd)

    @Test
    fun aPlayingTrackBecomesPlaybackWithTheSessionClock() {
        assertEquals(
            PlaybackState(
                "spotify:track:1",
                "T",
                "A",
                200_000,
                10_000 - 340,
                isPaused = false,
                capturedAtElapsedMs = 500,
            ),
            snap().toPlaybackState(-340),
        )
    }

    @Test
    fun pausedBufferingAndStoppedAreNotPlaying() {
        listOf(STATE_PAUSED, STATE_BUFFERING, STATE_STOPPED).forEach {
            assertTrue(snap(state = it).toPlaybackState(0)!!.isPaused)
        }
    }

    /** A medida mostrou o erro na extrapolação depois de tocar: parado, a posição é a do Spotify. */
    @Test
    fun aPausedPositionIsNotCorrected() {
        assertEquals(10_000L, snap(state = STATE_PAUSED).toPlaybackState(-340)!!.positionMs)
    }

    @Test
    fun notATrackIsNoPlayback() {
        assertNull(snap(mediaId = "spotify:episode:9").toPlaybackState(0))
        assertNull(snap(mediaId = null).toPlaybackState(0))
        assertNull(snap(isAd = true).toPlaybackState(0))
    }

    @Test
    fun negativePositionsAreClampedToZero() {
        assertEquals(0L, snap(positionMs = 100).toPlaybackState(-340)!!.positionMs)
    }
}
