package com.rodrigomachado.riff.feature.gameplay

import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.game.ChartAudio
import com.rodrigomachado.riff.core.data.game.ChartNotFoundException
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameplayViewModelTest : GameplayViewModelTestBase() {
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
        assertEquals(session.engine.longestCleanMs, record.cleanStreakMs) // "Tempo sem errar" vai para o ranking
        // O recorde vai ao ranking com o id gravado e o nome da música, uma vez só.
        val (sent, song) = ranking.submitted.single()
        assertEquals(record.copy(id = 1L), sent)
        assertEquals("Música — Banda", song)
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

    /** Sem guitarra separada, a biblioteca é como o Spotify: a música toca inteira e o acerto fica em silêncio. */
    @Test
    fun libraryWithoutSeparateGuitarHasNoHitSoundLikeSpotify() = runTest {
        loader.result = { playable() }
        val state = viewModel().uiState.value as GameplayUiState.Ready
        player.positionMs = 1_040
        time.now = GameSession.SYNC_INTERVAL_MS
        state.session.tick()

        state.session.press(0)

        assertTrue(state.session.lastJudgment != null)
        assertEquals(0, sfx.hits)
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

    @Test
    fun aCallOrUnpluggedHeadphonesPauseTheLibraryGame() = runTest {
        loader.result = { playable() }
        val viewModel = viewModel()
        assertEquals(1, interruptions.starts) // pediu o foco de áudio ao começar

        interruptions.interrupt()

        assertTrue((viewModel.uiState.value as GameplayUiState.Ready).isPaused)
        assertEquals(false, player.isPlaying)
        viewModel.resume()
        assertEquals(2, interruptions.starts) // pede o foco de novo ao continuar
        viewModel.release()
        assertTrue(interruptions.stopped)
    }

    @Test
    fun resumingDuringACallPausesAgainSayingTheAudioIsBusy() = runTest {
        loader.result = { playable() }
        val viewModel = viewModel()
        interruptions.interrupt()

        viewModel.resume()
        interruptions.deny()

        val paused = viewModel.uiState.value as GameplayUiState.Ready
        assertTrue(paused.isPaused)
        assertTrue(paused.audioBusy)
        viewModel.resume()
        assertEquals(false, (viewModel.uiState.value as GameplayUiState.Ready).audioBusy)
    }

    @Test
    fun openingDuringACallPausesSayingTheAudioIsBusy() = runTest {
        loader.result = { playable() }
        val viewModel = viewModel()

        interruptions.deny()

        val paused = viewModel.uiState.value as GameplayUiState.Ready
        assertTrue(paused.isPaused)
        assertTrue(paused.audioBusy)
    }

    @Test
    fun aCorruptedChartIsExplainedAsUnreadableNotAsMissing() = runTest {
        loader.result = { throw IllegalStateException("nota inválida na linha 42") } // o leitor do mapa falhou

        assertEquals(GameplayUiState.Unavailable(UnavailableReason.CHART_UNREADABLE), viewModel().uiState.value)
    }

    @Test
    fun aPermissionRevokedWhileReadingTheChartIsExplainedAsLostFolderAccess() = runTest {
        loader.result = { throw SecurityException("permissão revogada no meio da leitura") }

        assertEquals(GameplayUiState.Unavailable(UnavailableReason.FOLDER_ACCESS_LOST), viewModel().uiState.value)
    }

    @Test
    fun aFailureOutsideTheChartIsNotBlamedOnTheChart() = runTest {
        loader.result = { playable() }
        chartOffsets.failure = IllegalStateException("banco indisponível")

        assertEquals(GameplayUiState.Unavailable(UnavailableReason.UNEXPECTED), viewModel().uiState.value)
    }

    @Test
    fun withTheOptionOnTheMapImageComesWithTheMatch() = runTest {
        loader.result = { playable() }
        val vm = viewModel()

        val ready = vm.uiState.value as GameplayUiState.Ready
        assertEquals(art, ready.art)
        assertEquals(listOf("a"), artRequests)
    }

    @Test
    fun withTheOptionOffThereIsNoImage() = runTest {
        loader.result = { playable() }
        settings.settings.value = settings.settings.value.copy(showChartImage = false)
        val vm = viewModel()

        assertNull((vm.uiState.value as GameplayUiState.Ready).art)
        assertTrue(artRequests.isEmpty())
    }

    @Test
    fun theMapImageIsReadBeforeTheSongStarts() = runTest {
        loader.result = { playable() }
        viewModel()

        assertEquals(false, playingWhenArtAsked)
        assertTrue(player.isPlaying)
    }

    @Test
    fun aMapImageThatFailsDoesNotStopTheMatch() = runTest {
        loader.result = { playable() }
        artFailure = SecurityException("permissão perdida")
        val vm = viewModel()

        val ready = vm.uiState.value as GameplayUiState.Ready
        assertNull(ready.art)
    }
}
