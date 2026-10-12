package com.rodrigomachado.riff.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Quadros durante 20 s de partida, sem e com o perfil. Só o jogo conta: abrir a música e sair ficam de fora. */
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
        // Sem modo de abertura: com COLD, o app é fechado entre a preparação e a medição, e a biblioteca some.
        startupMode = null,
        setupBlock = {
            // Fecha o app aqui (e não entre a preparação e a medição): cada repetição começa da tela inicial.
            killProcess()
            pressHome()
            startActivityAndWait()
            waitForHome()
            browseLibrary()
            check(openSong()) { "'${songTitle()}' não está na biblioteca: importe a música ou passe -Priff.song" }
        },
    ) {
        tapLanes()
    }
}
