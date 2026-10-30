package com.rodrigomachado.riff.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

const val PACKAGE = "com.rodrigomachado.riff"
const val TAG = "RiffDesempenho"

// Etiquetas das telas (testTag com testTagsAsResourceId); as classes ficam nos módulos do app.
private const val OPEN_LIBRARY = "home_solo"
private const val LIBRARY_LIST = "library_list"
private const val DIFFICULTY_OPTION = "difficulty_option"
private const val STAGE = "gameplay_stage"
private const val PAUSE = "gameplay_pause"
private const val EXIT = "gameplay_exit"

private const val TIMEOUT_MS = 10_000L
private const val FIND_MS = 1_500L
private const val MAX_SCROLLS = 30
private const val SCROLL_PERCENT = 0.8f
private const val PLAY_MS = 20_000L
private const val TAP_EVERY_MS = 250L
private const val LANES = 5

// Linha de acerto e largura da pista nela (HighwayGeometry: strikeYFraction e strikeWidthFraction).
private const val STRIKE_Y_FRACTION = 0.82f
private const val STRIKE_WIDTH_FRACTION = 0.92f

/** Música do percurso da partida: argumento de instrumentação `riff.song`. */
fun songTitle(): String = InstrumentationRegistry.getArguments().getString("riff.song") ?: "Smells Like Teen Spirit"

/** Abertura: espera a tela inicial (o quadro Sozinho). */
fun MacrobenchmarkScope.waitForHome() {
    checkNotNull(device.wait(Until.findObject(By.res(OPEN_LIBRARY)), TIMEOUT_MS)) { "a tela inicial não apareceu" }
}

/** Biblioteca: abre e rola a lista até o fim e de volta. */
fun MacrobenchmarkScope.browseLibrary() {
    device.findObject(By.res(OPEN_LIBRARY)).click()
    checkNotNull(device.wait(Until.findObject(By.res(LIBRARY_LIST)), TIMEOUT_MS)) { "a biblioteca não abriu" }
    for (direction in arrayOf(Direction.DOWN, Direction.UP)) {
        // A lista é recomposta depois de cada rolagem: procura de novo em vez de guardar a referência.
        val list = device.wait(Until.findObject(By.res(LIBRARY_LIST)), TIMEOUT_MS) ?: continue
        try {
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(direction)
        } catch (_: StaleObjectException) {
            // Recomposta no meio do gesto: a rolagem já aconteceu.
        }
    }
    device.waitForIdle()
}

/**
 * Partida, com a biblioteca aberta: escolhe [songTitle], a primeira dificuldade, toca 20 s e sai pela pausa.
 * Falso se a música não está na biblioteca.
 */
fun MacrobenchmarkScope.playSong(): Boolean {
    if (!openSong()) return false
    tapLanes()
    leaveMatch()
    return true
}

/** Abre [songTitle] (rolando a lista até achar) na primeira dificuldade e espera a pista. Falso se não achou. */
fun MacrobenchmarkScope.openSong(): Boolean {
    val song = findSong() ?: return false
    song.click()
    checkNotNull(device.wait(Until.findObject(By.res(DIFFICULTY_OPTION)), TIMEOUT_MS)) { "sem diálogo de dificuldade" }
        .click()
    checkNotNull(device.wait(Until.findObject(By.res(STAGE)), TIMEOUT_MS)) { "a partida não abriu" }
    return true
}

/** A música pode estar mais abaixo na lista: rola para baixo até ela aparecer ou a lista acabar. */
private fun MacrobenchmarkScope.findSong(): UiObject2? {
    repeat(MAX_SCROLLS) {
        device.wait(Until.findObject(By.text(songTitle())), FIND_MS)?.let { return it }
        val list = device.findObject(By.res(LIBRARY_LIST)) ?: return null
        val moved = try {
            list.setGestureMargin(device.displayWidth / 5)
            list.scroll(Direction.DOWN, SCROLL_PERCENT)
        } catch (_: StaleObjectException) {
            true // recomposta no meio do gesto: a rolagem já aconteceu
        }
        if (!moved) return device.findObject(By.text(songTitle()))
    }
    return null
}

/** 20 s tocando numa trilha diferente a cada 250 ms (acertos, erros, chamas e cortes da guitarra). */
fun MacrobenchmarkScope.tapLanes() {
    val stage = checkNotNull(device.wait(Until.findObject(By.res(STAGE)), TIMEOUT_MS)) { "a pista não está na tela" }
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
}

/** Sai da partida pela pausa. */
fun MacrobenchmarkScope.leaveMatch() {
    device.findObject(By.res(PAUSE))?.click()
    device.wait(Until.findObject(By.res(EXIT)), TIMEOUT_MS)?.click()
    device.waitForIdle()
}
