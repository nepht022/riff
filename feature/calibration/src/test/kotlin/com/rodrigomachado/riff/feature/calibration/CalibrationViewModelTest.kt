package com.rodrigomachado.riff.feature.calibration

import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class CalibrationViewModelTest {
    private class FakeSettings : SettingsRepository {
        override val settings = MutableStateFlow(Settings.DEFAULT)

        override suspend fun update(transform: (Settings) -> Settings) {
            settings.value = transform(settings.value)
        }
    }

    private val settings = FakeSettings()
    private val clicks = List(ClickTrack.CLICK_COUNT) { 1_000L + it * 600L }
    private var finish = CompletableDeferred<List<Long>>()
    private var plays = 0
    private val player = ClickTrackPlayer {
        plays++
        finish.await()
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun tapAll(viewModel: CalibrationViewModel, lateMs: Long) =
        clicks.drop(CalibrationSession.LEAD_IN).forEach { viewModel.onTap(it + lateMs) }

    @Test
    fun aMeasurementShowsTheLatencyAndSavingStoresItAsANegativeOffset() = runTest {
        val viewModel = CalibrationViewModel(settings, player)
        assertEquals(CalibrationUiState.Instructions, viewModel.uiState.value)

        viewModel.start()
        tapAll(viewModel, lateMs = 42)
        assertEquals(CalibrationUiState.Measuring(taps = 20), viewModel.uiState.value)
        finish.complete(clicks)

        assertEquals(CalibrationUiState.Result(42), viewModel.uiState.value)
        viewModel.nudge(+5)
        viewModel.nudge(+5)
        assertEquals(CalibrationUiState.Result(52), viewModel.uiState.value)
        var saved = false
        viewModel.save { saved = true }
        assertEquals(-52, settings.settings.value.audioOffsetMs)
        assertTrue(saved)
    }

    @Test
    fun anUnsteadyMeasurementAsksToRepeat() = runTest {
        val viewModel = CalibrationViewModel(settings, player)

        viewModel.start()
        clicks.drop(CalibrationSession.LEAD_IN).take(5).forEach { viewModel.onTap(it + 40) }
        finish.complete(clicks)

        assertEquals(CalibrationUiState.Unstable, viewModel.uiState.value)
        assertEquals(Settings.DEFAULT.audioOffsetMs, settings.settings.value.audioOffsetMs)
    }

    @Test
    fun theSavedValueStaysWithinTheLimits() = runTest {
        val viewModel = CalibrationViewModel(settings, player)
        viewModel.start()
        tapAll(viewModel, lateMs = 280) // perto do máximo que cliques a cada 600 ms conseguem medir
        finish.complete(clicks)

        repeat(50) { viewModel.nudge(+5) }
        assertEquals(CalibrationUiState.Result(500), viewModel.uiState.value)
        viewModel.save {}

        assertEquals(-500, settings.settings.value.audioOffsetMs)
    }

    @Test
    fun cancellingStopsTheMeasurementAndGoesBackToTheStart() = runTest {
        val viewModel = CalibrationViewModel(settings, player)
        viewModel.start()
        viewModel.onTap(3_000)

        viewModel.cancel()
        finish.complete(clicks) // o metrônomo já foi cancelado: nada acontece

        assertEquals(CalibrationUiState.Instructions, viewModel.uiState.value)
        finish = CompletableDeferred()
        viewModel.start()
        assertEquals(CalibrationUiState.Measuring(taps = 0), viewModel.uiState.value)
        assertEquals(2, plays)
    }

    @Test
    fun anOutputThatDoesNotOpenIsExplained() = runTest {
        val viewModel = CalibrationViewModel(settings) { throw java.io.IOException("saída indisponível") }

        viewModel.start()

        assertEquals(CalibrationUiState.AudioFailed, viewModel.uiState.value)
    }

    @Test
    fun resettingClearsTheSavedCalibration() = runTest {
        settings.settings.value = Settings.DEFAULT.copy(audioOffsetMs = 99)
        val viewModel = CalibrationViewModel(settings, player)
        var done = false

        viewModel.reset { done = true }

        assertEquals(0, settings.settings.value.audioOffsetMs)
        assertTrue(done)
    }
}
