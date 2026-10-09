package com.rodrigomachado.riff.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Quadros durante 20 s de partida, sem e com o perfil. */
@RunWith(AndroidJUnit4::class)
class GameplayBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun gameplayWithoutProfile() = gameplay(CompilationMode.None())

    @Test
    fun gameplayWithProfile() = gameplay(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun gameplay(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        iterations = 3,
        startupMode = StartupMode.COLD,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            waitForHome()
            browseLibrary()
        },
    ) {
        check(playSong()) { "'${songTitle()}' não está na biblioteca: importe a música ou passe -Priff.song" }
    }
}
