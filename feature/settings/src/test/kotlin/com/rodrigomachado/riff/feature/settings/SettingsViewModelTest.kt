package com.rodrigomachado.riff.feature.settings

import com.rodrigomachado.riff.core.model.Settings
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val repository = FakeSettingsRepository()
    private val sfx = FakeSfxPlayer()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): SettingsViewModel {
        val viewModel = SettingsViewModel(repository) { sfx }
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
        viewModel.setMissVolume(0.9f)
        viewModel.setNoteSpeed(1.5f)
        viewModel.setLeftHanded(true)

        assertEquals(
            Settings.DEFAULT.copy(
                masterVolume = 0.8f,
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

        viewModel.setMissVolume(1.3f)
        viewModel.setNoteSpeed(9f)

        assertEquals(1f, repository.settings.value.missVolume)
        assertEquals(Settings.MAX_NOTE_SPEED, repository.settings.value.noteSpeed)
    }

    @Test
    fun adjustingAnEffectPlaysItAtTheNewVolume() = runTest {
        val viewModel = viewModel()

        viewModel.setMasterVolume(0.5f)
        viewModel.previewMiss()

        assertEquals(listOf("miss"), sfx.played)
        assertEquals(0.3f, sfx.missVolume, 1e-6f)
    }

    @Test
    fun leavingTheScreenReleasesTheSounds() = runTest {
        val viewModel = viewModel()
        viewModel.previewMiss()

        viewModel.release()

        assertTrue(sfx.released)
    }
}
