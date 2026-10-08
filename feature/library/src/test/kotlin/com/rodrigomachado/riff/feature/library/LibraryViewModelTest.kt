package com.rodrigomachado.riff.feature.library

import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.importer.ImportFailure
import com.rodrigomachado.riff.core.data.importer.ImportReport
import kotlinx.coroutines.CompletableDeferred
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

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): LibraryViewModel {
        val viewModel = LibraryViewModel(repository, folderStore, importer, folderAccess)
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
            ImportReport(importedCount = 1, failures = listOf(ImportFailure("Quebrada", "linha 3: nota inválida")))
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
}
