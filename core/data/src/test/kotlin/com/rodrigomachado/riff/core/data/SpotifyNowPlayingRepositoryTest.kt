package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.NowPlaying
import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.spotify.SpotifyRemote
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpotifyNowPlayingRepositoryTest {
    private val remote = FakeRemote()
    private val matches = FakeMatches()
    private val charts = FakeCharts()
    private val repository = SpotifyNowPlayingRepository(remote, matches, charts)
    private val track = PlaybackState("spotify:track:x", "Música", "Banda", 200_000, 0, false, 0)
    private val chart =
        Chart("a", "Música", "Banda", null, null, null, "u", ChartFormat.CHART, setOf(Difficulty.EXPERT))

    @Test
    fun followsTheTrackAndRecomputesTheMatchWhenAChoiceIsSaved() = runTest {
        val seen = mutableListOf<NowPlaying?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.nowPlaying.collect { seen += it } }
        assertNull(seen.last())

        remote.playback.value = track
        assertEquals(NowPlaying(track, MatchStatus.NoMatch), seen.last())

        matches.result = MatchStatus.Matched(chart, 0)
        matches.changes.value++
        assertEquals(NowPlaying(track, MatchStatus.Matched(chart, 0)), seen.last())
    }

    @Test
    fun connectIsForwardedToSpotify() {
        repository.connect()
        assertEquals(1, remote.connects)
        assertEquals(SpotifyConnection.Idle, repository.connection.value)
    }

    private class FakeRemote : SpotifyRemote {
        var connects = 0
        override val connection = MutableStateFlow<SpotifyConnection>(SpotifyConnection.Idle)
        override val playback = MutableStateFlow<PlaybackState?>(null)

        override fun connect() {
            connects++
        }

        override suspend fun refresh(): PlaybackState? = playback.value

        override fun play(trackUri: String) = Unit

        override fun pause() = Unit

        override fun resume() = Unit

        override fun seekTo(positionMs: Long) = Unit
    }

    private class FakeMatches : TrackMatchRepository {
        var result: MatchStatus = MatchStatus.NoMatch
        override val changes = MutableStateFlow(0)

        override suspend fun matchFor(track: PlaybackState) = result

        override suspend fun save(spotifyTrackUri: String, chartId: String, confidence: Float, isManual: Boolean) = Unit

        override suspend fun offsetFor(spotifyTrackUri: String, chartId: String): Int? = null

        override suspend fun saveOffset(spotifyTrackUri: String, chartId: String, offsetMs: Int) = Unit
    }

    private class FakeCharts : ChartRepository {
        override fun observeCharts(): Flow<List<Chart>> = MutableStateFlow(emptyList())

        override suspend fun getChart(id: String): Chart? = null

        override suspend fun saveScanned(chart: Chart, scanId: Long) = Unit

        override suspend fun removeNotSeenIn(scanId: Long) = Unit

        override suspend fun keepFolder(folderUri: String, scanId: Long) = Unit
    }
}
