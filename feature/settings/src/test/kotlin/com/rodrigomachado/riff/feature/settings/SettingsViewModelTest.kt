package com.rodrigomachado.riff.feature.settings

import com.rodrigomachado.riff.core.model.AppLanguage
import com.rodrigomachado.riff.core.model.FretStyle
import com.rodrigomachado.riff.core.model.HighwayArt
import com.rodrigomachado.riff.core.model.HighwayBackground
import com.rodrigomachado.riff.core.model.NoteStyle
import com.rodrigomachado.riff.core.model.SeparationUsage
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.model.TrackLook
import com.rodrigomachado.riff.core.spotify.web.WebAuthState
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val repository = FakeSettingsRepository()
    private val sfx = FakeSfxPlayer()
    private val separations = FakeAiSeparations()
    private val language = FakeAppLanguage()
    private val webAuth = FakeWebAuth()
    private val folder = FakeLibraryFolder()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): SettingsViewModel {
        val viewModel = SettingsViewModel(repository, separations, language, webAuth, folder, folder) { sfx }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun showsTheSavedSettings() = runTest {
        repository.settings.value = Settings.DEFAULT.copy(leftHanded = true)

        assertEquals(Settings.DEFAULT.copy(leftHanded = true), viewModel().uiState.value)
    }

    @Test
    fun eachChangeIsSavedRightAway() = runTest {
        val viewModel = viewModel()

        viewModel.setMasterVolume(0.8f)
        viewModel.setHitVolume(0.1f)
        viewModel.setMissVolume(0.9f)
        viewModel.setNoteSpeed(1.5f)
        viewModel.setLeftHanded(true)

        assertEquals(
            Settings.DEFAULT.copy(
                masterVolume = 0.8f,
                hitVolume = 0.1f,
                missVolume = 0.9f,
                noteSpeed = 1.5f,
                leftHanded = true,
            ),
            repository.settings.value,
        )
    }

    @Test
    fun outOfRangeValuesAreKeptInsideTheLimits() = runTest {
        val viewModel = viewModel()

        viewModel.setHitVolume(1.3f)
        viewModel.setNoteSpeed(9f)

        assertEquals(1f, repository.settings.value.hitVolume)
        assertEquals(Settings.MAX_NOTE_SPEED, repository.settings.value.noteSpeed)
    }

    @Test
    fun adjustingAnEffectPlaysItAtTheNewVolume() = runTest {
        val viewModel = viewModel()

        viewModel.setMasterVolume(0.5f)
        viewModel.setHitVolume(0.4f)
        viewModel.previewHit()
        viewModel.previewMiss()

        assertEquals(listOf("hit:2", "miss"), sfx.played)
        assertEquals(0.2f, sfx.hitVolume, 1e-6f)
        assertEquals(0.3f, sfx.missVolume, 1e-6f)
    }

    @Test
    fun leavingTheScreenReleasesTheSounds() = runTest {
        val viewModel = viewModel()
        viewModel.previewHit()

        viewModel.release()

        assertTrue(sfx.released)
    }

    @Test
    fun theAiSectionShowsTheUsageAndDeletesOrAsksAgain() = runTest {
        separations.usage.value = SeparationUsage(songs = 12, bytes = 96_000_000)
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.separationUsage.collect {} }

        assertEquals(SeparationUsage(12, 96_000_000), vm.separationUsage.value)
        vm.deleteSeparations()
        vm.setAskSeparationAlways(true)
        advanceUntilIdle()

        assertEquals(listOf("deleteAll"), separations.calls)
        assertEquals(true, repository.settings.value.askSeparationAlways)
    }

    @Test
    fun theLookChoicesAndTheMapImageAreSaved() = runTest {
        val vm = viewModel()
        val look = TrackLook(HighwayBackground.FLAMES, HighwayArt.STARS, NoteStyle.PICKS, FretStyle.PEDALS)

        vm.setTrackLook(look)
        vm.setShowChartImage(false)
        advanceUntilIdle()

        assertEquals(look, repository.settings.value.trackLook)
        assertEquals(false, repository.settings.value.showChartImage)
    }

    @Test
    fun choosingALanguageChangesIt() = runTest {
        val vm = viewModel()

        vm.setLanguage(AppLanguage.ENGLISH)

        assertEquals(AppLanguage.ENGLISH, language.current)
    }

    @Test
    fun turningPrefetchOnAsksForAuthorization() = runTest {
        val vm = viewModel()

        vm.setQueuePrefetch(true)
        advanceUntilIdle()

        assertEquals(1, webAuth.asked)
        assertEquals(true, repository.settings.value.queuePrefetch)
        assertEquals(null, vm.queueMessage.value)
    }

    @Test
    fun aRefusedAuthorizationKeepsItOffAndSaysWhy() = runTest {
        webAuth.accept = false
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.queueMessage.collect {} }

        vm.setQueuePrefetch(true)
        advanceUntilIdle()

        assertEquals(false, repository.settings.value.queuePrefetch)
        assertEquals(QueueMessage.REFUSED, vm.queueMessage.value)
    }

    @Test
    fun alreadyAuthorizedTurnsOnWithoutAsking() = runTest {
        webAuth.state.value = WebAuthState.AUTHORIZED
        val vm = viewModel()

        vm.setQueuePrefetch(true)
        advanceUntilIdle()

        assertEquals(0, webAuth.asked)
        assertEquals(true, repository.settings.value.queuePrefetch)
    }

    @Test
    fun aLostAuthorizationIsExplained() = runTest {
        webAuth.state.value = WebAuthState.LOST
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.queueMessage.collect {} }

        assertEquals(QueueMessage.LOST, vm.queueMessage.value)
    }

    @Test
    fun theOtherQueueOptionsAreSaved() = runTest {
        val vm = viewModel()

        vm.setQueueDeleteUnplayed(false)
        vm.setQueueUseMobileData(true)
        vm.setQueueReplaceSuggested(false)
        vm.onQueue(QueueAction.ClosestDuration(false))
        advanceUntilIdle()

        val saved = repository.settings.value
        assertEquals(false, saved.queueDeleteUnplayed)
        assertEquals(true, saved.queueUseMobileData)
        assertEquals(false, saved.queueReplaceSuggested)
        assertEquals(false, saved.queueClosestDuration)
    }

    @Test
    fun theFolderPermissionIsReported() = runTest {
        folder.writable = false
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.canWriteToLibrary.collect {} }
        advanceUntilIdle()

        assertEquals(false, vm.canWriteToLibrary.value)
    }
}
