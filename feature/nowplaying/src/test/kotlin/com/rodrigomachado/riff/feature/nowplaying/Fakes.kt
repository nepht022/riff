package com.rodrigomachado.riff.feature.nowplaying

import com.rodrigomachado.riff.core.data.NowPlayingRepository
import com.rodrigomachado.riff.core.data.TrackMatchRepository
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.NowPlaying
import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyConnection
import kotlinx.coroutines.flow.MutableStateFlow

private val difficulties = setOf(Difficulty.MEDIUM, Difficulty.EXPERT)

fun chart(id: String = "a", title: String = "Bohemian Rhapsody", artist: String = "Queen") =
    Chart(id, title, artist, null, null, null, "content://$id", ChartFormat.CHART, difficulties)

fun track(isPaused: Boolean = false) =
    PlaybackState("spotify:track:x", "Bohemian Rhapsody - Remastered 2011", "Queen", 355_000, 61_000, isPaused, 0)

fun playing(match: MatchStatus, isPaused: Boolean = false) = NowPlaying(track(isPaused), match)

class FakeNowPlayingRepository : NowPlayingRepository {
    var connects = 0
    override val connection = MutableStateFlow<SpotifyConnection>(SpotifyConnection.Idle)
    override val nowPlaying = MutableStateFlow<NowPlaying?>(null)
    override val playback = MutableStateFlow<PlaybackState?>(null)

    val commands = mutableListOf<String>()

    override fun connect() {
        connects++
    }

    override fun previous() {
        commands += "previous"
    }

    override fun next() {
        commands += "next"
    }

    override fun previousTrack() {
        commands += "previousTrack"
    }

    override fun pause() {
        commands += "pause"
    }

    override fun resume() {
        commands += "resume"
    }
}

class FakeTrackMatchRepository : TrackMatchRepository {
    val saved = mutableListOf<Triple<String, String, Boolean>>()
    override val changes = MutableStateFlow(0)

    /** O casamento que o banco tem agora (pode estar à frente do que a tela já recebeu). */
    val statuses = mutableMapOf<String, MatchStatus>()

    override suspend fun matchFor(track: PlaybackState): MatchStatus = statuses[track.trackUri] ?: MatchStatus.NoMatch

    override suspend fun saveSuggestion(spotifyTrackUri: String, chartId: String, confidence: Float) = Unit

    override suspend fun save(spotifyTrackUri: String, chartId: String, confidence: Float, isManual: Boolean) {
        saved += Triple(spotifyTrackUri, chartId, isManual)
    }

    override suspend fun offsetFor(spotifyTrackUri: String, chartId: String): Int? = null

    override suspend fun saveOffset(spotifyTrackUri: String, chartId: String, offsetMs: Int) = Unit
}
