package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyError
import com.spotify.android.appremote.api.error.AuthenticationFailedException
import com.spotify.android.appremote.api.error.CouldNotFindSpotifyApp
import com.spotify.android.appremote.api.error.NotLoggedInException
import com.spotify.android.appremote.api.error.OfflineModeException
import com.spotify.android.appremote.api.error.SpotifyConnectionTerminatedException
import com.spotify.android.appremote.api.error.SpotifyDisconnectedException
import com.spotify.android.appremote.api.error.UserNotAuthorizedException
import com.spotify.protocol.types.Artist
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyMappersTest {
    private fun track(isEpisode: Boolean = false) = Track(
        Artist("DragonForce", "spotify:artist:1"),
        listOf(Artist("DragonForce", "spotify:artist:1")),
        null,
        441_000,
        "Through the Fire and Flames",
        "spotify:track:ttfaf",
        null,
        isEpisode,
        false,
    )

    private fun state(track: Track?, isPaused: Boolean = false) = PlayerState(track, isPaused, 1f, 12_000, null, null)

    @Test
    fun playerStateBecomesPlaybackStateWithTheCaptureTime() {
        assertEquals(
            PlaybackState(
                trackUri = "spotify:track:ttfaf",
                title = "Through the Fire and Flames",
                artist = "DragonForce",
                durationMs = 441_000,
                positionMs = 12_000,
                isPaused = true,
                capturedAtElapsedMs = 500,
            ),
            state(track(), isPaused = true).toPlaybackState(capturedAtMs = 500),
        )
    }

    @Test
    fun nothingPlayingOrAPodcastIsNotATrackToPlay() {
        assertNull(state(null).toPlaybackState(0))
        assertNull(state(track(isEpisode = true)).toPlaybackState(0))
    }

    @Test
    fun sdkErrorsBecomeReasonsTheScreenCanExplain() {
        val cause = RuntimeException()
        assertEquals(SpotifyError.NOT_INSTALLED, CouldNotFindSpotifyApp().toSpotifyError())
        assertEquals(SpotifyError.NOT_LOGGED_IN, NotLoggedInException("", cause).toSpotifyError())
        assertEquals(SpotifyError.NOT_AUTHORIZED, UserNotAuthorizedException("", cause).toSpotifyError())
        assertEquals(SpotifyError.NOT_AUTHORIZED, AuthenticationFailedException("", cause).toSpotifyError())
        assertEquals(SpotifyError.OFFLINE, OfflineModeException("", cause).toSpotifyError())
        assertEquals(SpotifyError.DISCONNECTED, SpotifyDisconnectedException().toSpotifyError())
        assertEquals(SpotifyError.DISCONNECTED, SpotifyConnectionTerminatedException().toSpotifyError())
        assertEquals(SpotifyError.UNKNOWN, IllegalStateException().toSpotifyError())
    }
}
