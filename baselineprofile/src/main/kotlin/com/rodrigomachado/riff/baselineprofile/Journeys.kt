package com.rodrigomachado.riff.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val PACKAGE = "com.rodrigomachado.riff"
const val TAG = "RiffDesempenho"

// Etiquetas das telas (testTag com testTagsAsResourceId); as classes ficam nos módulos do app.
private const val OPEN_LIBRARY = "nowplaying_open_library"
private const val LIBRARY_LIST = "library_list"
private const val DIFFICULTY_OPTION = "difficulty_option"
private const val STAGE = "gameplay_stage"
private const val PAUSE = "gameplay_pause"
private const val EXIT = "gameplay_exit"

private const val TIMEOUT_MS = 10_000L
private const val PLAY_MS = 20_000L
private const val TAP_EVERY_MS = 250L
private const val LANES = 5

// Linha de acerto e largura da pista nela (HighwayGeometry: strikeYFraction e strikeWidthFraction).
private const val STRIKE_Y_FRACTION = 0.82f
private const val STRIKE_WIDTH_FRACTION = 0.92f

/** Música do percurso da partida: argumento de instrumentação `riff.song`. */
fun songTitle(): String = InstrumentationRegistry.getArguments().getString("riff.song") ?: "Smells Like Teen Spirit"

/** Abertura: espera a tela inicial ("Tocando agora"). */
fun MacrobenchmarkScope.waitForHome() {
    checkNotNull(device.wait(Until.findObject(By.res(OPEN_LIBRARY)), TIMEOUT_MS)) { "a tela inicial não apareceu" }
}

/** Biblioteca: abre e rola a lista até o fim e de volta. */
fun MacrobenchmarkScope.browseLibrary() {
    device.findObject(By.res(OPEN_LIBRARY)).click()
    val list = checkNotNull(
        device.wait(Until.findObject(By.res(LIBRARY_LIST)), TIMEOUT_MS),
    ) { "a biblioteca não abriu" }
    list.setGestureMargin(device.displayWidth / 5)
    list.fling(Direction.DOWN)
    list.fling(Direction.UP)
    device.waitForIdle()
}

/**
 * Partida, com a biblioteca aberta: escolhe [songTitle], a primeira dificuldade, toca 20 s numa trilha
 * diferente a cada 250 ms e sai pela pausa. Falso se a música não está na biblioteca.
 */
fun MacrobenchmarkScope.playSong(): Boolean {
    val song = device.wait(Until.findObject(By.text(songTitle())), TIMEOUT_MS) ?: return false
    song.click()
    checkNotNull(device.wait(Until.findObject(By.res(DIFFICULTY_OPTION)), TIMEOUT_MS)) { "sem diálogo de dificuldade" }
        .click()
    val stage = checkNotNull(device.wait(Until.findObject(By.res(STAGE)), TIMEOUT_MS)) { "a partida não abriu" }
    val bounds = stage.visibleBounds
    val strikeY = bounds.top + (bounds.height() * STRIKE_Y_FRACTION).toInt()
    val laneWidth = bounds.width() * STRIKE_WIDTH_FRACTION / LANES
    val left = bounds.left + bounds.width() * (1 - STRIKE_WIDTH_FRACTION) / 2
    val end = SystemClock.uptimeMillis() + PLAY_MS
    var lane = 0
    while (SystemClock.uptimeMillis() < end) {
        device.click((left + laneWidth * (lane + 0.5f)).toInt(), strikeY)
        lane = (lane + 1) % LANES
        SystemClock.sleep(TAP_EVERY_MS)
    }
    device.findObject(By.res(PAUSE))?.click()
    device.wait(Until.findObject(By.res(EXIT)), TIMEOUT_MS)?.click()
    device.waitForIdle()
    return true
}
