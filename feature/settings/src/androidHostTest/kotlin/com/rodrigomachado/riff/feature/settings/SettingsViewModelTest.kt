package com.rodrigomachado.riff.feature.settings

import com.rodrigomachado.riff.core.model.AppLanguage
import com.rodrigomachado.riff.core.model.FretStyle
import com.rodrigomachado.riff.core.model.HighwayArt
import com.rodrigomachado.riff.core.model.HighwayBackground
import com.rodrigomachado.riff.core.model.NoteStyle
import com.rodrigomachado.riff.core.model.SeparationUsage
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.model.TrackLook
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
    private val folder = FakeLibraryFolder()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val ranking = object : com.rodrigomachado.riff.core.data.ranking.RankingRepository {
        override val isConfigured = false
        override val player = kotlinx.coroutines.flow.MutableStateFlow(
            com.rodrigomachado.riff.core.data.ranking.Player(null, null),
        )
        override val askedLater = kotlinx.coroutines.flow.MutableStateFlow(false)

        override fun askLater() = Unit

        override suspend fun register(name: String) = com.rodrigomachado.riff.core.data.ranking.NameResult.Offline

        override fun submitIfBest(
            record: com.rodrigomachado.riff.core.model.ScoreRecord,
            title: String,
            artist: String,
        ) = Unit

        override fun statusFor(scoreId: Long) =
            kotlinx.coroutines.flow.MutableStateFlow<com.rodrigomachado.riff.core.data.ranking.SubmitStatus?>(null)

        override suspend fun flush() = Unit

        override suspend fun songs(query: String) = com.rodrigomachado.riff.core.data.ranking.ApiAnswer.Offline

        override suspend fun board(chartId: String) = com.rodrigomachado.riff.core.data.ranking.ApiAnswer.Offline
    }

    private fun TestScope.viewModel(): SettingsViewModel {
        val viewModel = SettingsViewModel(repository, separations, language, ranking) { sfx }
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

    @Test
    fun theAiSectionShowsTheUsageAndDeletesOrAsksAgain() = runTest {
        separations.usage.value = SeparationUsage(songs = 12, bytes = 96_000_000)
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.separationUsage.collect {} }

        assertEquals(SeparationUsage(12, 96_000_000), vm.separationUsage.value)
        vm.deleteSeparations()
        vm.setAskSeparationAlways(true)
        vm.setSeparateOnlyCharging(true)
        advanceUntilIdle()

        assertEquals(listOf("deleteAll"), separations.calls)
        assertEquals(true, repository.settings.value.askSeparationAlways)
        assertEquals(true, repository.settings.value.separateOnlyCharging)
    }

    @Test
    fun theLookChoicesAndTheMapImageAreSaved() = runTest {
        val vm = viewModel()
        val look = TrackLook(HighwayBackground.FLAMES, HighwayArt.STARS, NoteStyle.PICKS, FretStyle.PEDALS)

        vm.setTrackLook { look }
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

    /** A tela ainda mostrava o visual antigo: a segunda escolha não desfaz a primeira. */
    @Test
    fun aQuickSecondChoiceKeepsTheFirst() = runTest {
        val vm = viewModel()

        vm.setTrackLook { it.copy(background = HighwayBackground.NEON) }
        vm.setTrackLook { it.copy(art = HighwayArt.STARS) }
        advanceUntilIdle()

        assertEquals(HighwayBackground.NEON, repository.settings.value.highwayBackground)
        assertEquals(HighwayArt.STARS, repository.settings.value.highwayArt)
    }
}
