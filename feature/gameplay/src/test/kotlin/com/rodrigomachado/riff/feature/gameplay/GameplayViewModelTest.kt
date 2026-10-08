package com.rodrigomachado.riff.feature.gameplay

import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.game.ChartNotFoundException
import com.rodrigomachado.riff.core.data.game.PlayableChart
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Settings
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
    private val settings = FakeSettingsRepository(Settings.DEFAULT.copy(sfxVolume = 0.3f, audioOffsetMs = -40))
    private val scores = FakeScoreRepository()
    private val player = FakeSongPlayer()
    private val sfx = FakeSfxPlayer()
    private var playersCreated = 0
    private val time = FakeTime()

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

    private fun viewModel() = GameplayViewModel(
        SavedStateHandle(mapOf("chartId" to "a", "difficulty" to "EXPERT")),
        loader,
        settings,
        scores,
        {
            playersCreated++
            player
        },
        { sfx },
        time,
    )

    private fun playable(audio: String? = "raiz/a/song.ogg") =
        PlayableChart(chart, Difficulty.EXPERT, listOf(note(1_000), note(2_000)), audio)

    @Test
    fun loadsPreparesTheSongAndStartsPlaying() = runTest {
        loader.result = { playable() }

        val state = viewModel().uiState.value

        assertTrue(state is GameplayUiState.Ready)
        assertEquals("raiz/a/song.ogg", player.preparedUri)
        assertTrue(player.isPlaying)
        assertEquals(0.3f, sfx.lastVolume)
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
}
