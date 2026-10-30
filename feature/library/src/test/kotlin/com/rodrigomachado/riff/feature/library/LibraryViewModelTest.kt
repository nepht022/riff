package com.rodrigomachado.riff.feature.library

import com.rodrigomachado.riff.core.data.AiSeparationState
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.game.ChartAudio
import com.rodrigomachado.riff.core.data.importer.ImportFailure
import com.rodrigomachado.riff.core.data.importer.ImportFailureKind
import com.rodrigomachado.riff.core.data.importer.ImportReport
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.QueuedSeparation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val repository = FakeChartRepository()
    private val folderStore = FakeLibraryFolderStore()
    private val folderAccess = FakeFolderAccess()
    private val importer = FakeChartImporter()
    private val matches = FakeTrackMatchRepository()
    private val separations = FakeAiSeparations()
    private val scheduler = FakeScheduler()
    private val probe = FakeAudioProbe()
    private val settings = FakeSettingsRepository()
    private val deleter = FakeChartDeleter(repository)
    private val song = Chart("m", "Música", "Banda", null, null, null, "u", ChartFormat.CHART, setOf(Difficulty.EXPERT))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): LibraryViewModel {
        val viewModel = LibraryViewModel(
            repository,
            folderStore,
            importer,
            folderAccess,
            matches,
            separations,
            scheduler,
            probe,
            settings,
            deleter,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun withoutFolderAsksToChooseOne() = runTest {
        val state = viewModel().uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasFolder)
    }

    @Test
    fun pickingAFolderPersistsAccessRemembersItAndImports() = runTest {
        importer.nextResult = {
            repository.charts.value = listOf(chart("a"))
            val broken = ImportFailure("Quebrada", ImportFailureKind.INVALID_CHART, "linha 3: nota inválida")
            ImportReport(importedCount = 1, failures = listOf(broken))
        }
        val viewModel = viewModel()

        viewModel.onFolderPicked("content://pasta")

        val state = viewModel.uiState.value
        assertEquals(listOf("content://pasta"), folderAccess.persisted)
        assertEquals("content://pasta", folderStore.rootUri.value)
        assertEquals(listOf("content://pasta"), importer.calls)
        assertTrue(state.hasFolder)
        assertFalse(state.isImporting)
        assertEquals(listOf("a"), state.charts.map { it.id })
        assertEquals(1, state.report?.importedCount)
    }

    @Test
    fun importingShowsProgress() = runTest {
        importer.gate = CompletableDeferred()
        importer.progressToReport = 3
        val viewModel = viewModel()

        viewModel.onFolderPicked("content://pasta")

        assertTrue(viewModel.uiState.value.isImporting)
        assertEquals(3, viewModel.uiState.value.scannedFolders)
        importer.gate!!.complete(Unit)
        assertFalse(viewModel.uiState.value.isImporting)
    }

    @Test
    fun rescanImportsTheRememberedFolder() = runTest {
        folderStore.rootUri.value = "content://guardada"
        val viewModel = viewModel()

        viewModel.onRescan()

        assertEquals(listOf("content://guardada"), importer.calls)
    }

    @Test
    fun lostAccessShowsErrorAndKeepsTheList() = runTest {
        folderStore.rootUri.value = "content://pasta"
        repository.charts.value = listOf(chart("a"))
        importer.nextResult = { throw FolderAccessLostException("revogada") }
        val viewModel = viewModel()

        viewModel.onRescan()

        assertTrue(viewModel.uiState.value.folderAccessLost)
        assertEquals(listOf("a"), viewModel.uiState.value.charts.map { it.id })
    }

    @Test
    fun cancellingThePickerAfterLosingAccessGoesBackToTheList() = runTest {
        folderStore.rootUri.value = "content://pasta"
        repository.charts.value = listOf(chart("a"))
        importer.nextResult = { throw FolderAccessLostException("revogada") }
        val viewModel = viewModel()
        viewModel.onRescan()

        viewModel.onFolderPickCancelled() // tocou em "Escolher pasta" e desistiu

        assertFalse(viewModel.uiState.value.folderAccessLost)
        assertEquals(listOf("a"), viewModel.uiState.value.charts.map { it.id })
    }

    @Test
    fun folderThatCannotBePersistedShowsError() = runTest {
        folderAccess.failure = SecurityException("não persistível")
        val viewModel = viewModel()

        viewModel.onFolderPicked("content://pasta")

        assertTrue(viewModel.uiState.value.folderAccessLost)
        assertTrue(importer.calls.isEmpty())
    }

    @Test
    fun dismissingTheReportClearsIt() = runTest {
        folderStore.rootUri.value = "content://pasta"
        importer.nextResult = { ImportReport(importedCount = 2, failures = emptyList()) }
        val viewModel = viewModel()
        viewModel.onRescan()

        viewModel.onReportDismissed()

        assertNull(viewModel.uiState.value.report)
    }

    @Test
    fun unexpectedImportErrorShowsErrorInsteadOfCrashing() = runTest {
        folderStore.rootUri.value = "content://pasta"
        repository.charts.value = listOf(chart("a"))
        importer.nextResult = { throw IllegalStateException("disco cheio") }
        val viewModel = viewModel()

        viewModel.onRescan()

        assertTrue(viewModel.uiState.value.importFailed)
        assertFalse(viewModel.uiState.value.isImporting)
        assertEquals(listOf("a"), viewModel.uiState.value.charts.map { it.id })
    }

    @Test
    fun choosingAChartForATrackSavesAManualMatch() = runTest {
        val viewModel = viewModel()

        viewModel.chooseChartFor("spotify:track:x", "a")

        assertEquals(listOf(Triple("spotify:track:x", "a", true)), matches.saved)
        assertTrue(viewModel.uiState.value.matchSaved)
    }

    @Test
    fun aSongWithoutSeparateGuitarAsksBeforePlaying() = runTest {
        probe.audio["m"] = ChartAudio.Single("u/song.opus")
        val vm = viewModel()

        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()

        assertEquals(SeparationAsk("m", "Música", Difficulty.EXPERT, lowMemory = false), vm.uiState.value.prompt)
        assertNull(vm.uiState.value.play)
    }

    @Test
    fun aSongWithItsOwnGuitarPlaysRightAway() = runTest {
        probe.audio["m"] = ChartAudio.Stems("g", listOf("s"), "s")
        val vm = viewModel()

        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()

        assertEquals(PlayRequestLibrary("m", Difficulty.EXPERT), vm.uiState.value.play)
    }

    @Test
    fun aDeclinedOrSeparatedSongPlaysRightAway() = runTest {
        probe.audio["m"] = ChartAudio.Single("u/song.opus")
        separations.states["m"] = AiSeparationState.DECLINED
        settings.settings.value = settings.settings.value.copy(askSeparationAlways = false)
        val vm = viewModel()

        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()

        assertEquals(PlayRequestLibrary("m", Difficulty.EXPERT), vm.uiState.value.play)
    }

    @Test
    fun playingWithoutSeparatingIsRememberedAndPlays() = runTest {
        probe.audio["m"] = ChartAudio.Single("u/song.opus")
        val vm = viewModel()
        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()

        vm.onPlayWithoutSeparating()
        advanceUntilIdle()

        assertEquals(AiSeparationState.DECLINED, separations.states["m"])
        assertEquals(PlayRequestLibrary("m", Difficulty.EXPERT), vm.uiState.value.play)
        assertNull(vm.uiState.value.prompt)

        vm.onPlayHandled()
        assertNull(vm.uiState.value.play)
    }

    @Test
    fun separatingStartsInTheBackgroundAndDoesNotPlay() = runTest {
        probe.audio["m"] = ChartAudio.Single("u/song.opus")
        val vm = viewModel()
        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.prompt?.busy)

        vm.onSeparate()

        assertEquals(listOf("m"), scheduler.enqueued)
        assertNull(vm.uiState.value.prompt)
        assertNull(vm.uiState.value.play)
    }

    @Test
    fun withOneSeparatingThePromptPutsTheSongInTheQueue() = runTest {
        probe.audio["m"] = ChartAudio.Single("u/song.opus")
        scheduler.running("outra")
        val vm = viewModel()
        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.prompt?.busy)
        vm.onSeparate()

        assertEquals(listOf("m"), scheduler.enqueued)
        assertNull(vm.uiState.value.prompt)
    }

    @Test
    fun withAQueueWaitingThePromptAlsoSaysItGoesToTheQueue() = runTest {
        probe.audio["m"] = ChartAudio.Single("u/song.opus")
        scheduler.queue.value = listOf(QueuedSeparation("outra", "Outra"))
        val vm = viewModel()

        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.prompt?.busy)
    }

    @Test
    fun aSongAlreadyInTheQueuePlaysWithoutAsking() = runTest {
        probe.audio["m"] = ChartAudio.Single("u/song.opus")
        scheduler.queue.value = listOf(QueuedSeparation("m", "Música"))
        val vm = viewModel()

        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()

        assertEquals(PlayRequestLibrary("m", Difficulty.EXPERT), vm.uiState.value.play)
    }

    @Test
    fun theSongBeingSeparatedPlaysWholeWithoutAsking() = runTest {
        probe.audio["m"] = ChartAudio.Single("u/song.opus")
        scheduler.running("m")
        val vm = viewModel()

        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()

        assertEquals(PlayRequestLibrary("m", Difficulty.EXPERT), vm.uiState.value.play)
    }

    @Test
    fun aRescanPrunesSeparationsOfSongsThatLeft() = runTest {
        folderStore.rootUri.value = "content://pasta"
        importer.nextResult = { ImportReport(importedCount = 1, failures = emptyList()) }
        val vm = viewModel()

        vm.onRescan()
        advanceUntilIdle()

        assertEquals(1, separations.pruned)
    }

    @Test
    fun separatedSongsAreMarked() = runTest {
        separations.separatedIds.value = setOf("m")

        assertEquals(setOf("m"), viewModel().uiState.value.aiSeparated)
    }

    @Test
    fun aChartWhoseFolderIsGoneStillGoesToPlayInsteadOfCrashing() = runTest {
        probe.failure = com.rodrigomachado.riff.core.data.folder.FolderAccessLostException("pasta sumiu")
        val vm = viewModel()

        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()

        assertEquals(PlayRequestLibrary("m", Difficulty.EXPERT), vm.uiState.value.play)
    }

    @Test
    fun playingFromABusyPromptDoesNotRememberADecline() = runTest {
        probe.audio["m"] = ChartAudio.Single("u/song.opus")
        scheduler.running("outra")
        val vm = viewModel()
        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()

        vm.onPlayWithoutSeparating()
        advanceUntilIdle()

        assertNull(separations.states["m"])
        assertEquals(PlayRequestLibrary("m", Difficulty.EXPERT), vm.uiState.value.play)
    }

    @Test
    fun withAskAlwaysOnADeclinedSongAsksAgainButASeparatedOneDoesNot() = runTest {
        probe.audio["m"] = ChartAudio.Single("u/song.opus")
        probe.audio["s"] = ChartAudio.Single("u/s.opus")
        separations.states["m"] = AiSeparationState.DECLINED
        separations.states["s"] = AiSeparationState.SEPARATED
        settings.settings.value = settings.settings.value.copy(askSeparationAlways = true)
        val vm = viewModel()

        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()
        assertEquals("m", vm.uiState.value.prompt?.chartId)

        vm.onPromptDismissed()
        vm.onDifficultyChosen(song.copy(id = "s"), Difficulty.EXPERT)
        advanceUntilIdle()
        assertEquals(PlayRequestLibrary("s", Difficulty.EXPERT), vm.uiState.value.play)
    }

    @Test
    fun backClosesThePromptWithoutPlayingOrRemembering() = runTest {
        probe.audio["m"] = ChartAudio.Single("u/song.opus")
        val vm = viewModel()
        vm.onDifficultyChosen(song, Difficulty.EXPERT)
        advanceUntilIdle()

        vm.onPromptDismissed()

        assertNull(vm.uiState.value.prompt)
        assertNull(vm.uiState.value.play)
        assertNull(separations.states["m"])
    }

    private fun libraryWith(vararg charts: Chart) {
        folderStore.rootUri.value = "raiz"
        repository.charts.value = charts.toList()
    }

    private fun at(id: String, folder: String) =
        Chart(id, "Música $id", "Banda", null, null, null, folder, ChartFormat.CHART, setOf(Difficulty.EXPERT))

    @Test
    fun theXAsksToConfirmNamingTheSongAndWhatGoesWithIt() = runTest {
        libraryWith(at("a", "raiz/A"))
        deleter.nested = 1
        val vm = viewModel()

        vm.onDeleteRequested(listOf("a"))
        advanceUntilIdle()

        assertEquals(DeleteAsk(listOf("a"), title = "Música a", alsoDeleted = 1), vm.uiState.value.deletion.ask)
    }

    @Test
    fun confirmingDeletesAndLeavesSelection() = runTest {
        libraryWith(at("a", "raiz/A"), at("b", "raiz/B"), at("c", "raiz/C"))
        val vm = viewModel()
        vm.onStartSelecting()
        vm.onToggleSelected("a")
        vm.onToggleSelected("c")
        vm.onToggleSelected("a")
        vm.onToggleSelected("b")
        assertEquals(setOf("b", "c"), vm.uiState.value.deletion.selected)

        vm.onDeleteSelected()
        advanceUntilIdle()
        assertEquals(DeleteAsk(listOf("b", "c"), title = null, alsoDeleted = 0), vm.uiState.value.deletion.ask)
        vm.onDeleteConfirmed()
        advanceUntilIdle()

        assertEquals(listOf(listOf("b", "c")), deleter.deleted)
        assertEquals(listOf("a"), vm.uiState.value.charts.map { it.id })
        assertEquals(DeletionState(), vm.uiState.value.deletion)
    }

    @Test
    fun aFailedDeletionIsReported() = runTest {
        libraryWith(at("a", "raiz/A"))
        deleter.failing = setOf("a")
        val vm = viewModel()
        vm.onDeleteRequested(listOf("a"))
        advanceUntilIdle()

        vm.onDeleteConfirmed()
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.deletion.failed)
        vm.onDeletionFailureShown()
        assertEquals(0, vm.uiState.value.deletion.failed)
    }

    @Test
    fun selectAllMarksEveryDeletableChartAndTheSecondTapClearsThem() = runTest {
        libraryWith(at("solto", "raiz"), at("a", "raiz/A"), at("b", "raiz/B"))
        val vm = viewModel()
        vm.onStartSelecting()
        vm.onToggleSelected("a")

        vm.onToggleAll()
        assertEquals(setOf("a", "b"), vm.uiState.value.deletion.selected)

        vm.onToggleAll()
        assertEquals(emptySet<String>(), vm.uiState.value.deletion.selected)
        assertEquals(true, vm.uiState.value.deletion.selecting)
    }

    @Test
    fun aChartLooseInTheRootCannotBeDeleted() = runTest {
        libraryWith(at("solto", "raiz"), at("a", "raiz/A"))

        assertEquals(setOf("solto"), viewModel().uiState.value.protectedIds)
    }

    @Test
    fun withoutPermissionToWriteItAsksToAllowFirst() = runTest {
        libraryWith(at("a", "raiz/A"))
        folderAccess.writable = false
        val vm = viewModel()

        vm.onDeleteRequested(listOf("a"))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.deletion.needsPermission)
        assertNull(vm.uiState.value.deletion.ask)
    }

    @Test
    fun deletingTheSongBeingSeparatedStopsTheSeparation() = runTest {
        libraryWith(at("a", "raiz/A"))
        scheduler.running("a")
        val vm = viewModel()
        vm.onDeleteRequested(listOf("a"))
        advanceUntilIdle()

        vm.onDeleteConfirmed()
        advanceUntilIdle()

        assertTrue(scheduler.cancelled)
    }

    @Test
    fun separatingTheSelectionQueuesOnlyTheSongsThatNeedItInListOrder() = runTest {
        libraryWith(
            at("a", "raiz/A"),
            at("b", "raiz/B").copy(separateGuitar = true),
            at("c", "raiz/C"),
            at("d", "raiz/D"),
            at("e", "raiz/E"),
        )
        separations.separatedIds.value = setOf("c")
        scheduler.queue.value = listOf(QueuedSeparation("d", "Música d"))
        val vm = viewModel()
        vm.onStartSelecting()
        listOf("e", "d", "c", "b", "a").forEach(vm::onToggleSelected)

        assertEquals(2, vm.uiState.value.separableSelected)
        vm.onSeparateSelected()

        assertEquals(listOf("a", "e"), scheduler.enqueued)
        assertFalse(vm.uiState.value.deletion.selecting)
    }

    @Test
    fun theSongBeingSeparatedDoesNotCountForTheSelection() = runTest {
        libraryWith(at("a", "raiz/A"))
        scheduler.running("a")
        val vm = viewModel()
        vm.onStartSelecting()
        vm.onToggleSelected("a")

        assertEquals(0, vm.uiState.value.separableSelected)
    }
}
