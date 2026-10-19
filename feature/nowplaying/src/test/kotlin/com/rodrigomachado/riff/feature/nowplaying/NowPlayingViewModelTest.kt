package com.rodrigomachado.riff.feature.nowplaying

import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.data.queue.MemoryQueueMisses
import com.rodrigomachado.riff.core.data.queue.MemoryQueuePreparing
import com.rodrigomachado.riff.core.data.queue.QueueMiss
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.SpotifyConnection
import com.rodrigomachado.riff.core.model.SpotifyError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModelTest {
    private val repository = FakeNowPlayingRepository()
    private val matches = FakeTrackMatchRepository()
    private val misses = MemoryQueueMisses()
    private val preparing = MemoryQueuePreparing()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val saved = SavedStateHandle()

    private fun TestScope.viewModel(): NowPlayingViewModel {
        val viewModel = NowPlayingViewModel(repository, matches, misses, preparing).also { it.watchAutoPlay(saved) }
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
        viewModel.onReturn()
        assertEquals(2, repository.connects)
    }

    /** A fila procurou no Encore e não achou: a tela conta, mas só enquanto a música estiver sem mapa. */
    @Test
    fun whatTheQueueFoundIsShownOnlyWithoutAMap() = runTest {
        val viewModel = viewModel()
        repository.connection.value = SpotifyConnection.Connected
        misses.record("spotify:track:x", QueueMiss.NOTHING_FOUND)

        val none = playing(MatchStatus.NoMatch)
        repository.nowPlaying.value = none
        assertEquals(NowPlayingUiState.Playing(none, QueueMiss.NOTHING_FOUND), viewModel.uiState.value)

        val found = playing(MatchStatus.Matched(chart(), 0))
        repository.nowPlaying.value = found
        assertEquals(NowPlayingUiState.Playing(found), viewModel.uiState.value)
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

        repository.connection.value = SpotifyConnection.Failed(SpotifyError.NO_PERMISSION)
        viewModel.onReturn() // ligou "Acesso às notificações" e voltou
        assertEquals(3, repository.connects)
    }

    /** O jogador pode ter desligado o "Acesso às notificações" lá fora: voltar ao app sempre confere de novo. */
    @Test
    fun comingBackWhileConnectedChecksThePermissionAgain() = runTest {
        val viewModel = viewModel()
        repository.connection.value = SpotifyConnection.Connected

        viewModel.onReturn()

        assertEquals(2, repository.connects)
    }

    @Test
    fun anAdIsShownWhileNothingElsePlays() = runTest {
        val viewModel = viewModel()
        repository.connection.value = SpotifyConnection.Connected
        repository.adPlaying.value = true

        assertEquals(NowPlayingUiState.Ad, viewModel.uiState.value)
    }

    /** Conta grátis no modo contínuo: o anúncio vem entre as músicas; a próxima partida espera ele acabar. */
    @Test
    fun anAdDoesNotCancelTheNextSong() = runTest {
        val viewModel = viewModel()
        songChangedDuringAMatch(Difficulty.EXPERT)

        repository.nowPlaying.value = null
        repository.adPlaying.value = true
        advanceTimeBy(NowPlayingAutoPlay.WAIT_MS + 30_000)
        repository.adPlaying.value = false
        repository.nowPlaying.value = nextSong(MatchStatus.Matched(chart("b"), trackOffsetMs = 0))

        assertEquals(
            NowPlayingAutoPlay.Action.Start(PlayRequest("b", Difficulty.EXPERT, "spotify:track:y", 0)),
            viewModel.autoPlay.value,
        )
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

    /** A partida pediu a próxima música (pausa → ⏭): a tela espera o Spotify trocar e decide se já começa. */
    private fun songChangedDuringAMatch(difficulty: Difficulty = Difficulty.EXPERT) {
        repository.connection.value = SpotifyConnection.Connected
        repository.nowPlaying.value = playing(MatchStatus.Matched(chart(), 0)) // a música da partida
        NowPlayingAutoPlay.request(saved, difficulty, fromTrackUri = "spotify:track:x")
    }

    private fun nextSong(match: MatchStatus) =
        playing(match).let { it.copy(playback = it.playback.copy(trackUri = "spotify:track:y")) }

    @Test
    fun aNewSongWithAMapStartsRightAwayInTheSameDifficulty() = runTest {
        val viewModel = viewModel()
        songChangedDuringAMatch(Difficulty.EXPERT)

        repository.nowPlaying.value = nextSong(MatchStatus.Matched(chart("b"), trackOffsetMs = 30))

        assertEquals(
            NowPlayingAutoPlay.Action.Start(PlayRequest("b", Difficulty.EXPERT, "spotify:track:y", 30)),
            viewModel.autoPlay.value,
        )
        viewModel.autoPlayHandled()
        assertNull(viewModel.autoPlay.value)
    }

    @Test
    fun aMapWithoutThatDifficultyAsksForOne() = runTest {
        val viewModel = viewModel()
        songChangedDuringAMatch(Difficulty.EASY) // o mapa de teste só tem Médio e Expert

        repository.nowPlaying.value = nextSong(MatchStatus.Matched(chart("b"), 0))

        assertEquals(NowPlayingAutoPlay.Action.ChooseDifficulty("spotify:track:y"), viewModel.autoPlay.value)
    }

    @Test
    fun noMapStaysOnThisScreen() = runTest {
        val viewModel = viewModel()
        songChangedDuringAMatch()

        repository.nowPlaying.value = nextSong(MatchStatus.NoMatch)

        assertNull(viewModel.autoPlay.value)
        // O pedido foi usado: a próxima troca de música, feita aqui, não começa partida nenhuma.
        repository.nowPlaying.value = nextSong(MatchStatus.Matched(chart("c"), 0)).let {
            it.copy(playback = it.playback.copy(trackUri = "spotify:track:z"))
        }
        assertNull(viewModel.autoPlay.value)
    }

    @Test
    fun ifSpotifyDoesNotChangeTheSongNothingStarts() = runTest {
        val viewModel = viewModel()
        songChangedDuringAMatch()

        advanceTimeBy(NowPlayingAutoPlay.WAIT_MS + 1)
        repository.nowPlaying.value = nextSong(MatchStatus.Matched(chart("b"), 0))

        assertNull(viewModel.autoPlay.value)
    }

    /** O Spotify já passou para outra música: a partida pronta para a anterior não começa mais. */
    @Test
    fun aReadyStartIsDroppedWhenTheSongChangesAgain() = runTest {
        val viewModel = viewModel()
        songChangedDuringAMatch()
        repository.nowPlaying.value = nextSong(MatchStatus.Matched(chart("b"), 0))

        repository.nowPlaying.value = nextSong(MatchStatus.Matched(chart("c"), 0)).let {
            it.copy(playback = it.playback.copy(trackUri = "spotify:track:z"))
        }

        assertNull(viewModel.autoPlay.value)
    }

    /** Só sugestão: a partida abre pausada pedindo para confirmar (spec Spotify contínuo §2.3). */
    @Test
    fun aSuggestionWithTheDifficultyAsksToConfirmInTheMatch() = runTest {
        val viewModel = viewModel()
        songChangedDuringAMatch(Difficulty.EXPERT)

        repository.nowPlaying.value = nextSong(MatchStatus.Suggested(chart("b"), 0.8f))

        assertEquals(
            NowPlayingAutoPlay.Action.Confirm(
                PlayRequest("b", Difficulty.EXPERT, "spotify:track:y", 0, confirmFirst = true),
            ),
            viewModel.autoPlay.value,
        )
    }

    @Test
    fun aSuggestionWithoutTheDifficultyStaysHere() = runTest {
        val viewModel = viewModel()
        songChangedDuringAMatch(Difficulty.EASY) // o mapa de teste só tem Médio e Expert

        repository.nowPlaying.value = nextSong(MatchStatus.Suggested(chart("b"), 0.8f))

        assertNull(viewModel.autoPlay.value)
    }

    /** Do fim da partida: o Spotify pode estar no fim da música (outro), então o prazo vem no pedido. */
    @Test
    fun theWaitComesFromTheRequest() = runTest {
        val viewModel = viewModel()
        repository.connection.value = SpotifyConnection.Connected
        repository.nowPlaying.value = playing(MatchStatus.Matched(chart(), 0))
        NowPlayingAutoPlay.request(saved, Difficulty.EXPERT, fromTrackUri = "spotify:track:x", waitMs = 20_000)

        advanceTimeBy(15_000)
        repository.nowPlaying.value = nextSong(MatchStatus.Matched(chart("b"), 0))

        assertEquals(
            NowPlayingAutoPlay.Action.Start(PlayRequest("b", Difficulty.EXPERT, "spotify:track:y", 0)),
            viewModel.autoPlay.value,
        )
    }

    /** A fila ainda baixa o mapa da música nova: espera, e decide com o casamento do banco (não o da tela). */
    @Test
    fun afterTheDownloadTheDecisionSeesTheNewMap() = runTest {
        val viewModel = viewModel()
        preparing.start("spotify:track:y")
        songChangedDuringAMatch()
        repository.nowPlaying.value = nextSong(MatchStatus.NoMatch)
        assertNull(viewModel.autoPlay.value)

        matches.statuses["spotify:track:y"] = MatchStatus.Matched(chart("b"), 0)
        preparing.done("spotify:track:y")

        assertEquals(
            NowPlayingAutoPlay.Action.Start(PlayRequest("b", Difficulty.EXPERT, "spotify:track:y", 0)),
            viewModel.autoPlay.value,
        )
    }

    /** O Spotify passou de música durante a espera: decidir pela anterior puxaria o Spotify de volta para ela. */
    @Test
    fun aSongThatPassedDuringTheDownloadStartsNothing() = runTest {
        val viewModel = viewModel()
        preparing.start("spotify:track:y")
        songChangedDuringAMatch()
        repository.nowPlaying.value = nextSong(MatchStatus.NoMatch)

        repository.nowPlaying.value = nextSong(MatchStatus.NoMatch).let {
            it.copy(playback = it.playback.copy(trackUri = "spotify:track:z"))
        }
        matches.statuses["spotify:track:y"] = MatchStatus.Matched(chart("b"), 0)
        preparing.done("spotify:track:y")

        assertNull(viewModel.autoPlay.value)
    }

    @Test
    fun leavingWhileWaitingForTheDownloadStartsNothing() = runTest {
        val viewModel = viewModel()
        preparing.start("spotify:track:y")
        songChangedDuringAMatch()
        repository.nowPlaying.value = nextSong(MatchStatus.NoMatch)

        viewModel.cancelAutoPlay()
        matches.statuses["spotify:track:y"] = MatchStatus.Matched(chart("b"), 0)
        preparing.done("spotify:track:y")

        assertNull(viewModel.autoPlay.value)
    }

    @Test
    fun whatIsPreparingIsShown() = runTest {
        val viewModel = viewModel()
        repository.connection.value = SpotifyConnection.Connected
        preparing.start("spotify:track:x")

        val now = playing(MatchStatus.NoMatch)
        repository.nowPlaying.value = now

        assertEquals(NowPlayingUiState.Playing(now, null, preparingMap = true), viewModel.uiState.value)
    }

    /** Saiu do Tocando agora durante a espera: nada começa sozinho depois. */
    @Test
    fun leavingTheScreenCancelsTheRequest() = runTest {
        val viewModel = viewModel()
        songChangedDuringAMatch()

        viewModel.cancelAutoPlay()
        repository.nowPlaying.value = nextSong(MatchStatus.Matched(chart("b"), 0))

        assertNull(viewModel.autoPlay.value)
    }
}
