package com.rodrigomachado.riff.feature.gameplay

import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.game.ChartAudio
import com.rodrigomachado.riff.core.data.game.ChartNotFoundException
import com.rodrigomachado.riff.core.data.game.PlayableChart
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameplayViewModelTest {
    private val loader = FakeChartTrackLoader()
    private val settings = FakeSettingsRepository(Settings.DEFAULT.copy(hitVolume = 0.3f, audioOffsetMs = -40))
    private val scores = FakeScoreRepository()
    private val player = FakeSongPlayer()
    private val sfx = FakeSfxPlayer()
    private var playersCreated = 0
    private val spotifyPlayer = FakeSongPlayer()
    private var spotifyPlayersCreated = 0
    private val stemPlayer = FakeStemPlayer()
    private var stemsCreated: Pair<String, List<String>>? = null
    private val time = FakeTime()
    private val chartOffsets = FakeChartOffsetRepository()
    private val trackMatches = FakeTrackMatchRepository()

    private val chart = Chart(
        "a", "Música", "Banda", null, null, null, "raiz/a", ChartFormat.CHART,
        setOf(
            Difficulty.EXPERT,
        ),
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private var musicStartMs: Long? = null
    private val analyzed = mutableListOf<List<String>>()

    private fun viewModel(spotifyTrackUri: String? = null, trackOffsetMs: Int = 0) = GameplayViewModel(
        SavedStateHandle(
            buildMap {
                put("chartId", "a")
                put("difficulty", "EXPERT")
                spotifyTrackUri?.let { put("spotifyTrackUri", it) }
                put("trackOffsetMs", trackOffsetMs)
            },
        ),
        loader,
        settings,
        scores,
        chartOffsets,
        trackMatches,
        { uris ->
            analyzed += uris
            musicStartMs
        },
        {
            playersCreated++
            player
        },
        {
            spotifyPlayersCreated++
            spotifyPlayer
        },
        { guitar, backing ->
            stemsCreated = guitar to backing
            stemPlayer
        },
        { sfx },
        time,
    )

    private fun playable(audio: String? = "raiz/a/song.ogg") =
        PlayableChart(chart, Difficulty.EXPERT, listOf(note(1_000), note(2_000)), audio?.let { ChartAudio.Single(it) })

    @Test
    fun loadsPreparesTheSongAndStartsPlaying() = runTest {
        loader.result = { playable() }

        val state = viewModel().uiState.value

        assertTrue(state is GameplayUiState.Ready)
        assertEquals("raiz/a/song.ogg", player.preparedUri)
        assertTrue(player.isPlaying)
        assertEquals(0.3f, sfx.hitVolume)
    }

    @Test
    fun folderWithoutAudioExplainsWhyAndCreatesNoPlayer() = runTest {
        loader.result = { playable(audio = null) }

        assertEquals(GameplayUiState.Unavailable(UnavailableReason.NO_AUDIO), viewModel().uiState.value)
        assertEquals(0, playersCreated)
    }

    @Test
    fun missingChartAndLostFolderAreExplained() = runTest {
        loader.result = { throw ChartNotFoundException("sumiu") }
        assertEquals(GameplayUiState.Unavailable(UnavailableReason.CHART_MISSING), viewModel().uiState.value)

        loader.result = { throw FolderAccessLostException("revogada") }
        assertEquals(GameplayUiState.Unavailable(UnavailableReason.FOLDER_ACCESS_LOST), viewModel().uiState.value)
    }

    @Test
    fun audioThatCannotBePlayedIsExplainedAndReleased() = runTest {
        loader.result = { playable() }
        player.prepareFailure = java.io.IOException("formato desconhecido")

        assertEquals(GameplayUiState.Unavailable(UnavailableReason.AUDIO_FAILED), viewModel().uiState.value)
        assertTrue(player.released)
    }

    @Test
    fun pauseAndResumeAreReflectedInTheState() = runTest {
        loader.result = { playable() }
        val viewModel = viewModel()

        viewModel.pause()
        assertTrue((viewModel.uiState.value as GameplayUiState.Ready).isPaused)
        viewModel.resume()
        assertEquals(false, (viewModel.uiState.value as GameplayUiState.Ready).isPaused)
    }

    @Test
    fun finishingSavesTheScoreAndReleasesAudio() = runTest {
        loader.result = { playable() }
        val viewModel = viewModel()
        val session = (viewModel.uiState.value as GameplayUiState.Ready).session
        player.positionMs = 1_000
        time.now = GameSession.SYNC_INTERVAL_MS
        session.tick()
        session.press(0)

        viewModel.onSessionFinished()
        viewModel.onSessionFinished() // chamada repetida não grava de novo

        assertEquals(GameplayUiState.Finished(1), viewModel.uiState.value)
        val record = scores.saved.single()
        assertEquals("a", record.chartId)
        assertEquals(Difficulty.EXPERT, record.difficulty)
        assertEquals(100L, record.score)
        assertEquals(1, record.perfect)
        assertTrue(player.released)
        assertTrue(sfx.released)
    }

    @Test
    fun leavingMidSongReleasesTheAudio() = runTest {
        loader.result = { playable() }
        val viewModel = viewModel()

        viewModel.release()

        assertTrue(player.released)
        assertTrue(sfx.released)
        assertNull(scores.saved.firstOrNull())
    }

    @Test
    fun pauseAndResumeAfterLeavingDoNotTouchTheReleasedPlayer() = runTest {
        loader.result = { playable() }
        val viewModel = viewModel()

        viewModel.release()
        viewModel.pause()
        viewModel.resume()

        assertTrue(player.released)
    }

    @Test
    fun goingToBackgroundWhileTheScoreIsSavedDoesNotTouchTheReleasedPlayer() = runTest {
        loader.result = { playable() }
        scores.gate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onSessionFinished()
        viewModel.pause()
        scores.gate!!.complete(Unit)

        assertEquals(GameplayUiState.Finished(1), viewModel.uiState.value)
    }

    @Test
    fun goingToBackgroundWhileLoadingStartsTheGamePaused() = runTest {
        loader.result = { playable() }
        loader.gate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.pause()
        loader.gate!!.complete(Unit)

        assertTrue((viewModel.uiState.value as GameplayUiState.Ready).isPaused)
        assertEquals(false, player.isPlaying)
        viewModel.resume()
        assertTrue(player.isPlaying)
    }

    @Test
    fun spotifyModePlaysTheTrackWithItsOffsetAndNeedsNoAudioInTheFolder() = runTest {
        loader.result = { playable(audio = null) }

        val state = viewModel(
            spotifyTrackUri = "spotify:track:x",
            trackOffsetMs = 25,
        ).uiState.value as GameplayUiState.Ready

        assertEquals("spotify:track:x", spotifyPlayer.preparedUri)
        assertEquals(0, playersCreated)
        assertTrue(spotifyPlayer.isPlaying)
        spotifyPlayer.positionMs = 1_000
        time.now = GameSession.SYNC_INTERVAL_MS
        assertEquals(985L, state.session.tick()) // 1000 − 40 (calibração global) + 25 (ajuste da música)
    }

    @Test
    fun spotifyThatCannotPlayIsExplained() = runTest {
        loader.result = { playable() }
        spotifyPlayer.prepareFailure = java.io.IOException("Spotify indisponível")

        assertEquals(
            GameplayUiState.Unavailable(UnavailableReason.SPOTIFY_UNAVAILABLE),
            viewModel(spotifyTrackUri = "spotify:track:x").uiState.value,
        )
        assertTrue(spotifyPlayer.released)
    }

    @Test
    fun pausedOutsideTheGameShowsThePauseScreenAndReleasesTheLanes() = runTest {
        loader.result = { playable() }
        val viewModel = viewModel()
        val session = (viewModel.uiState.value as GameplayUiState.Ready).session
        session.press(0)

        viewModel.onPlaybackChanged(paused = true)

        assertTrue((viewModel.uiState.value as GameplayUiState.Ready).isPaused)
        assertEquals(false, session.isPressed(0))

        viewModel.onPlaybackChanged(paused = false)
        assertEquals(false, (viewModel.uiState.value as GameplayUiState.Ready).isPaused)
    }

    private fun stems() = ChartAudio.Stems(
        "raiz/a/guitar.ogg",
        listOf("raiz/a/song.ogg"),
        fallbackUri = "raiz/a/song.ogg",
    )

    @Test
    fun separateGuitarPlaysEveryTrackWithTheStemPlayer() = runTest {
        loader.result = { playable().copy(audio = stems()) }

        val state = viewModel().uiState.value as GameplayUiState.Ready

        assertEquals("raiz/a/guitar.ogg" to listOf("raiz/a/song.ogg"), stemsCreated)
        assertEquals(0, playersCreated)
        assertTrue(stemPlayer.isPlaying)
        assertEquals(false, state.withoutSeparateGuitar)
    }

    @Test
    fun stemsThatFailToPrepareFallBackToTheSingleFileWithANotice() = runTest {
        loader.result = { playable().copy(audio = stems()) }
        stemPlayer.song.prepareFailure = java.io.IOException("faixas com taxas diferentes")

        val state = viewModel().uiState.value as GameplayUiState.Ready

        assertTrue(stemPlayer.song.released)
        assertEquals("raiz/a/song.ogg", player.preparedUri)
        assertTrue(player.isPlaying)
        assertTrue(state.withoutSeparateGuitar)
    }

    @Test
    fun theSavedVolumesReachTheSongAndTheEffects() = runTest {
        settings.settings.value = Settings.DEFAULT.copy(masterVolume = 0.5f, hitVolume = 0.6f, missVolume = 1f)
        loader.result = { playable() }

        viewModel()

        assertEquals(0.5f, player.appVolume, 1e-6f)
        assertEquals(0.3f, sfx.hitVolume, 1e-6f)
        assertEquals(0.5f, sfx.missVolume, 1e-6f)
    }

    @Test
    fun theLibraryChartAdjustmentIsUsedAndSaved() = runTest {
        chartOffsets.offsets["a"] = 20
        loader.result = { playable() }
        val viewModel = viewModel()
        val ready = viewModel.uiState.value as GameplayUiState.Ready
        assertEquals(20, ready.songOffsetMs)
        player.positionMs = 1_000
        time.now = GameSession.SYNC_INTERVAL_MS
        assertEquals(980L, ready.session.tick()) // 1000 − 40 (geral) + 20 (mapa)

        viewModel.pause()
        viewModel.adjustSongOffset(+10)

        assertEquals(30, chartOffsets.offsets["a"])
        assertEquals(30, (viewModel.uiState.value as GameplayUiState.Ready).songOffsetMs)
        assertEquals(980L, ready.session.tick()) // pausado: a pista não anda
        viewModel.resume()
        time.now += GameSession.REWIND_ANIMATION_MS
        assertEquals(490L, ready.session.tick()) // a biblioteca volta 500 ms ao continuar; o ajuste já vale
    }

    @Test
    fun spotifyOffsetIsReadFromTheSavedMatchNotFromTheRoute() = runTest {
        trackMatches.offsets["spotify:track:x" to "a"] = -30
        loader.result = { playable() }

        val viewModel = viewModel(spotifyTrackUri = "spotify:track:x", trackOffsetMs = 25)
        viewModel.adjustSongOffset(-10)

        assertEquals(-40, (viewModel.uiState.value as GameplayUiState.Ready).songOffsetMs)
        assertEquals(-40, trackMatches.offsets["spotify:track:x" to "a"])
    }

    @Test
    fun songOffsetStopsAtTheLimits() = runTest {
        chartOffsets.offsets["a"] = 9_995
        loader.result = { playable() }
        val viewModel = viewModel()
        val ready = viewModel.uiState.value as GameplayUiState.Ready
        player.positionMs = 1_000
        time.now = GameSession.SYNC_INTERVAL_MS
        val before = ready.session.tick()

        viewModel.adjustSongOffset(+10)
        viewModel.adjustSongOffset(+10)

        assertEquals(10_000, chartOffsets.offsets["a"])
        assertEquals(before + 5, ready.session.tick())
    }

    @Test
    fun spotifyModeHasNoHitSoundBecauseTheWholeSongIsPlaying() = runTest {
        loader.result = { playable(audio = null) }
        val state = viewModel(spotifyTrackUri = "spotify:track:x").uiState.value as GameplayUiState.Ready
        spotifyPlayer.positionMs = 1_040
        time.now = GameSession.SYNC_INTERVAL_MS
        state.session.tick()

        state.session.press(0)

        assertTrue(state.session.lastJudgment != null)
        assertEquals(0, sfx.hits)
    }

    @Test
    fun libraryModeKeepsTheHitSound() = runTest {
        loader.result = { playable() }
        val state = viewModel().uiState.value as GameplayUiState.Ready
        player.positionMs = 1_040
        time.now = GameSession.SYNC_INTERVAL_MS
        state.session.tick()

        state.session.press(0)

        assertTrue(state.session.lastJudgment != null)
        assertEquals(1, sfx.hits)
    }

    @Test
    fun spotifyWithoutAnAdjustmentStartsAlignedWithWhereTheChartMusicBegins() = runTest {
        musicStartMs = 3_900 // o áudio do mapa tem uma contagem de entrada antes da banda
        loader.result = { playable() }

        val state = viewModel(spotifyTrackUri = "spotify:track:x").uiState.value as GameplayUiState.Ready

        assertEquals(listOf(listOf("raiz/a/song.ogg")), analyzed)
        assertEquals(3_900, state.songOffsetMs)
        assertEquals(3_900, trackMatches.offsets["spotify:track:x" to "a"])
    }

    @Test
    fun aSavedSpotifyAdjustmentIsKeptWithoutAnalyzing() = runTest {
        musicStartMs = 3_900
        trackMatches.offsets["spotify:track:x" to "a"] = 3_870
        loader.result = { playable() }

        val state = viewModel(spotifyTrackUri = "spotify:track:x").uiState.value as GameplayUiState.Ready

        assertEquals(emptyList<List<String>>(), analyzed)
        assertEquals(3_870, state.songOffsetMs)
    }

    @Test
    fun theLibraryPlaysItsOwnAudioAndNeedsNoAnalysis() = runTest {
        musicStartMs = 3_900
        loader.result = { playable() }

        val state = viewModel().uiState.value as GameplayUiState.Ready

        assertEquals(emptyList<List<String>>(), analyzed)
        assertEquals(0, state.songOffsetMs)
    }

    @Test
    fun theLibraryGoesBackHalfASecondWhenResuming() = runTest {
        loader.result = { playable() }
        val viewModel = viewModel()
        player.positionMs = 30_000
        viewModel.pause()

        viewModel.resume()

        assertEquals(listOf(29_500L), player.seeks)
    }

    @Test
    fun spotifyResumesExactlyWhereItStopped() = runTest {
        loader.result = { playable(audio = null) }
        val viewModel = viewModel(spotifyTrackUri = "spotify:track:x")
        spotifyPlayer.positionMs = 30_000
        viewModel.pause()

        viewModel.resume()

        assertEquals(emptyList<Long>(), spotifyPlayer.seeks)
    }

    @Test
    fun aSavedSpotifyAdjustmentOfZeroIsKeptWithoutAnalyzing() = runTest {
        musicStartMs = 3_900
        trackMatches.offsets["spotify:track:x" to "a"] = 0 // o jogador corrigiu a detecção para 0
        loader.result = { playable() }

        val state = viewModel(spotifyTrackUri = "spotify:track:x").uiState.value as GameplayUiState.Ready

        assertEquals(emptyList<List<String>>(), analyzed)
        assertEquals(0, state.songOffsetMs)
    }

    @Test
    fun anAnalysisThatFindsNoStartIsSavedSoItRunsOnlyOnce() = runTest {
        musicStartMs = null
        loader.result = { playable() }

        viewModel(spotifyTrackUri = "spotify:track:x")

        assertEquals(0, trackMatches.offsets["spotify:track:x" to "a"])
    }

    @Test
    fun aSongThatEndsBeforeTheChartCountsTheRestAsMisses() = runTest {
        loader.result = { playable() } // notas em 1.000 e 2.000
        val viewModel = viewModel()
        val session = (viewModel.uiState.value as GameplayUiState.Ready).session
        player.positionMs = 1_000
        time.now = GameSession.SYNC_INTERVAL_MS
        session.tick()
        session.press(0)
        player.isCompleted = true // o áudio acabou antes da segunda nota

        viewModel.onSessionFinished()

        val record = scores.saved.single()
        assertEquals(1, record.perfect)
        assertEquals(1, record.miss)
    }
}
