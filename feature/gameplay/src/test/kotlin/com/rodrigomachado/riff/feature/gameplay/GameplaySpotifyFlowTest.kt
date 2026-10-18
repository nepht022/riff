package com.rodrigomachado.riff.feature.gameplay

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.PlaybackState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spotify contínuo e ranking: o fim da partida, o painel de confirmação e o envio ao ranking. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameplaySpotifyFlowTest : GameplayViewModelTestBase() {
    private fun playbackOf(uri: String, positionMs: Long = 0, durationMs: Long = 200_000) =
        PlaybackState(uri, "Música", "Banda", durationMs, positionMs, isPaused = false, capturedAtElapsedMs = 0)

    /** Mapa só sugerido: a partida abre pausada, sem tocar a faixa, esperando a confirmação (spec §2.3). */
    @Test
    fun aSuggestionToConfirmOpensPausedWithoutPlaying() = runTest {
        loader.result = { playable(audio = null) }

        val state = viewModel(spotifyTrackUri = "spotify:track:x", confirmFirst = true).uiState.value

        state as GameplayUiState.Ready
        assertTrue(state.awaitingConfirmation)
        assertTrue(state.isPaused)
        assertEquals(false, spotifyPlayer.isPlaying)
    }

    @Test
    fun confirmingSavesTheMatchAndStarts() = runTest {
        loader.result = { playable(audio = null) }
        val viewModel = viewModel(spotifyTrackUri = "spotify:track:x", confirmFirst = true)

        viewModel.confirmAndPlay()

        assertEquals(listOf("spotify:track:x a 1.0 manual=true"), trackMatches.saved)
        val state = viewModel.uiState.value as GameplayUiState.Ready
        assertEquals(false, state.awaitingConfirmation)
        assertEquals(false, state.isPaused)
        assertTrue(spotifyPlayer.isPlaying)
    }

    /** O Spotify passou de música com o painel aberto: segue a corrente, sem confirmar a faixa errada. */
    @Test
    fun confirmAfterTheSongMovedOnDoesNothing() = runTest {
        loader.result = { playable(audio = null) }
        val viewModel = viewModel(spotifyTrackUri = "spotify:track:x", confirmFirst = true)

        controls.playback.value = playbackOf("spotify:track:y")
        assertEquals(GameplayUiState.NextSong(Difficulty.EXPERT, "spotify:track:x", 6_000), viewModel.uiState.value)
        viewModel.confirmAndPlay()

        assertTrue(trackMatches.saved.isEmpty())
        assertTrue(spotifyPlayer.released)
    }

    @Test
    fun theSameSongPlayingKeepsThePanel() = runTest {
        loader.result = { playable(audio = null) }
        val viewModel = viewModel(spotifyTrackUri = "spotify:track:x", confirmFirst = true)

        controls.playback.value = playbackOf("spotify:track:x", positionMs = 5_000)

        assertTrue((viewModel.uiState.value as GameplayUiState.Ready).awaitingConfirmation)
    }

    @Test
    fun confirmTwiceSavesOnce() = runTest {
        loader.result = { playable(audio = null) }
        val viewModel = viewModel(spotifyTrackUri = "spotify:track:x", confirmFirst = true)

        viewModel.confirmAndPlay()
        viewModel.confirmAndPlay()

        assertEquals(1, trackMatches.saved.size)
    }

    /** Spotify, "Ir direto para a próxima": o placar fica salvo e o Riff espera o que falta da música + 10 s. */
    @Test
    fun withNextSongDirectTheEndSavesAndGoesOn() = runTest {
        loader.result = { playable(audio = null) }
        val viewModel = viewModel(spotifyTrackUri = "spotify:track:x")
        controls.playback.value = playbackOf("spotify:track:x", positionMs = 170_000, durationMs = 200_000)

        viewModel.onSessionFinished()

        assertEquals(1, scores.saved.size)
        assertEquals(GameplayUiState.NextSong(Difficulty.EXPERT, "spotify:track:x", 40_000), viewModel.uiState.value)
        // As notas acabaram no outro: o Spotify segue tocando para passar para a próxima sozinho.
        assertTrue(spotifyPlayer.keptMusic)
    }

    /** "Ir direto para a próxima" pula o Resultado, mas a partida vai para o ranking do mesmo jeito. */
    @Test
    fun theFinishedMatchGoesToTheRankingEvenWhenTheResultIsSkipped() = runTest {
        loader.result = { playable(audio = null) }
        val viewModel = viewModel(spotifyTrackUri = "spotify:track:x")

        viewModel.onSessionFinished()

        assertTrue(viewModel.uiState.value is GameplayUiState.NextSong)
        val (sent, song) = ranking.submitted.single()
        assertEquals(1L, sent.id) // o id que o placar recebeu ao ser salvo
        assertEquals("a", sent.chartId)
        assertEquals("${chart.title} — ${chart.artist}", song)
    }

    @Test
    fun withShowResultTheEndGoesToTheResult() = runTest {
        settings.settings.value = settings.settings.value.copy(spotifyNextSongDirect = false)
        loader.result = { playable(audio = null) }
        val viewModel = viewModel(spotifyTrackUri = "spotify:track:x")

        viewModel.onSessionFinished()

        assertTrue(viewModel.uiState.value is GameplayUiState.Finished)
    }

    /** Sem estado do Spotify (desconectado): o prazo curto, para não esperar para sempre. */
    @Test
    fun withoutPlaybackTheNextSongWaitIsTheShortOne() = runTest {
        loader.result = { playable(audio = null) }
        val viewModel = viewModel(spotifyTrackUri = "spotify:track:x")

        viewModel.onSessionFinished()

        assertEquals(GameplayUiState.NextSong(Difficulty.EXPERT, "spotify:track:x", 6_000), viewModel.uiState.value)
    }

    /** O Spotify já trocou de faixa (foi o que acabou a partida): o prazo curto, não o da música nova. */
    @Test
    fun whenSpotifyAlreadyMovedOnTheWaitIsTheShortOne() = runTest {
        loader.result = { playable(audio = null) }
        val viewModel = viewModel(spotifyTrackUri = "spotify:track:x")
        controls.playback.value = playbackOf("spotify:track:y", positionMs = 1_000, durationMs = 300_000)

        viewModel.onSessionFinished()

        assertEquals(GameplayUiState.NextSong(Difficulty.EXPERT, "spotify:track:x", 6_000), viewModel.uiState.value)
    }

    @Test
    fun theLibraryAlwaysGoesToTheResult() = runTest {
        loader.result = { playable() }
        val viewModel = viewModel()

        viewModel.onSessionFinished()

        assertTrue(viewModel.uiState.value is GameplayUiState.Finished)
    }
}
