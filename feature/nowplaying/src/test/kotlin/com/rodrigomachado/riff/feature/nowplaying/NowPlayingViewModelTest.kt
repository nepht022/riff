package com.rodrigomachado.riff.feature.nowplaying

import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.model.SpotifyError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModelTest {
    private val repository = FakeNowPlayingRepository()
    private val matches = FakeTrackMatchRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): NowPlayingViewModel {
        val viewModel = NowPlayingViewModel(repository, matches)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun opensConnectingToSpotify() = runTest {
        val viewModel = viewModel()

        assertEquals(1, repository.connects)
        assertEquals(NowPlayingUiState.Connecting, viewModel.uiState.value)
    }

    @Test
    fun failureExplainsWhyAndRetryConnectsAgain() = runTest {
        val viewModel = viewModel()
        repository.connection.value = SpotifyConnection.Failed(SpotifyError.NOT_INSTALLED)

        assertEquals(NowPlayingUiState.Unavailable(SpotifyError.NOT_INSTALLED), viewModel.uiState.value)
        viewModel.retry()
        assertEquals(2, repository.connects)
    }

    @Test
    fun connectedShowsTheTrackOrThatNothingIsPlaying() = runTest {
        val viewModel = viewModel()
        repository.connection.value = SpotifyConnection.Connected
        assertEquals(NowPlayingUiState.NothingPlaying, viewModel.uiState.value)

        val now = playing(MatchStatus.Matched(chart(), 0))
        repository.nowPlaying.value = now
        assertEquals(NowPlayingUiState.Playing(now), viewModel.uiState.value)
    }

    @Test
    fun confirmingASuggestionSavesIt() = runTest {
        val viewModel = viewModel()

        viewModel.confirmSuggestion("spotify:track:x", "a", 0.9f)

        assertEquals(listOf(Triple("spotify:track:x", "a", false)), matches.saved)
    }

    @Test
    fun comingBackToTheAppTriesAgainIfSpotifyWasMissingOrLoggedOut() = runTest {
        val viewModel = viewModel()
        repository.connection.value = SpotifyConnection.Failed(SpotifyError.NOT_INSTALLED)

        viewModel.onReturn() // o jogador instalou o Spotify e voltou ao Riff
        assertEquals(2, repository.connects)

        repository.connection.value = SpotifyConnection.Failed(SpotifyError.NOT_LOGGED_IN)
        viewModel.onReturn()
        assertEquals(3, repository.connects)
    }

    @Test
    fun comingBackDoesNotRetryWhatCannotChangeWithTheAppOpen() = runTest {
        val viewModel = viewModel()
        repository.connection.value = SpotifyConnection.Failed(SpotifyError.NOT_CONFIGURED)

        viewModel.onReturn()
        repository.connection.value = SpotifyConnection.Connected
        viewModel.onReturn()

        assertEquals(1, repository.connects)
    }

    @Test
    fun theControlsCommandSpotify() = runTest {
        val viewModel = viewModel()

        viewModel.previous()
        viewModel.playPause(isPaused = false)
        viewModel.playPause(isPaused = true)
        viewModel.next()

        assertEquals(listOf("previous", "pause", "resume", "next"), repository.commands)
    }
}
