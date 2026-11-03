package com.rodrigomachado.riff.desktop.pad

import com.rodrigomachado.riff.core.ui.input.PadAction
import com.rodrigomachado.riff.core.ui.input.PadEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A thread `riff-pad` com uma fonte falsa: o GLFW não é iniciado (o CI não tem tela nem controle). */
class GlfwGamepadTest {
    /** Uma fonte que responde [pad] (nulo = nenhum controle) e conta as leituras. */
    private class FakeSource(private val ready: () -> Boolean = { true }) : PadSource {
        @Volatile var pad: PadSnapshot? = null

        @Volatile var polls = 0

        @Volatile var closed = false

        override fun init() = ready()

        override fun poll(): PadSnapshot? = pad.also { polls++ }

        override fun close() {
            closed = true
        }
    }

    /** A leitura (e o glfwInit) só começa quando o app pede, com a janela já aberta (o AWT escolhe a escala antes). */
    @Test
    fun theReadingStartsOnlyWhenAsked(): Unit = runBlocking {
        val source = FakeSource()
        GlfwGamepad(source).use { gamepad ->
            delay(50)
            assertEquals(0, source.polls)
            assertFalse(gamepad.isReading)

            gamepad.start()

            withTimeout(TIMEOUT_MS) { while (source.polls == 0) delay(1) }
        }
    }

    @Test
    fun aPadPluggedInLaterIsFound(): Unit = runBlocking {
        val source = FakeSource()
        GlfwGamepad(source).also { it.start() }.use { gamepad ->
            val first = withTimeout(TIMEOUT_MS) {
                gamepad.events.onSubscription {
                    while (source.polls < 2) delay(1)
                    assertFalse(gamepad.connected.value)
                    source.pad = PadSnapshot(mapped = true, setOf(MappedButton.Y))
                }.first()
            }
            assertEquals(PadAction.FRET_1, first.action)
            assertTrue(first.down)
            withTimeout(TIMEOUT_MS) { gamepad.connected.first { it } }
        }
    }

    /** O cabo saiu: as cores seguradas são soltas e o controle aparece desligado. */
    @Test
    fun aPadUnpluggedReleasesTheHeldFrets(): Unit = runBlocking {
        val source = FakeSource().apply { pad = PadSnapshot(mapped = true, setOf(MappedButton.Y)) }
        GlfwGamepad(source).also { it.start() }.use { gamepad ->
            withTimeout(TIMEOUT_MS) { gamepad.connected.first { it } }
            val released: PadEvent = withTimeout(TIMEOUT_MS) {
                gamepad.events.onSubscription { source.pad = null }.first()
            }
            assertEquals(PadEvent(PadAction.FRET_1, down = false, atNanos = released.atNanos), released)
            withTimeout(TIMEOUT_MS) { gamepad.connected.first { !it } }
        }
    }

    @Test
    fun withoutGlfwTheGameStillOpens() {
        val source = FakeSource(ready = { false })
        val gamepad = GlfwGamepad(source).also { it.start() }
        gamepad.awaitReadingThread()
        assertFalse(gamepad.connected.value)
        assertEquals(0, source.polls)
        assertClosesQuickly(gamepad)
    }

    /** O nativo do GLFW não carregou (`UnsatisfiedLinkError`): é o mesmo que não ter controle. */
    @Test
    fun aMissingNativeLibraryIsNoPad() {
        val source = FakeSource(ready = { throw UnsatisfiedLinkError("sem glfw") })
        val gamepad = GlfwGamepad(source).also { it.start() }
        gamepad.awaitReadingThread()
        assertFalse(gamepad.connected.value)
        assertClosesQuickly(gamepad)
    }

    @Test
    fun closingStopsTheReadingThread(): Unit = runBlocking {
        val source = FakeSource().apply { pad = PadSnapshot(mapped = true, emptySet()) }
        val gamepad = GlfwGamepad(source).also { it.start() }
        withTimeout(TIMEOUT_MS) { gamepad.connected.first { it } }
        assertClosesQuickly(gamepad)
        assertFalse(gamepad.isReading)
        assertTrue(source.closed)
        val polls = source.polls
        Thread.sleep(SETTLE_MS)
        assertEquals(polls, source.polls)
    }

    private fun GlfwGamepad.awaitReadingThread() {
        val end = System.currentTimeMillis() + TIMEOUT_MS
        while (isReading && System.currentTimeMillis() < end) Thread.sleep(1)
        assertFalse(isReading)
    }

    private fun assertClosesQuickly(gamepad: GlfwGamepad) {
        val start = System.nanoTime()
        gamepad.close()
        assertTrue((System.nanoTime() - start) / NANOS_PER_MS < TIMEOUT_MS)
    }

    private companion object {
        const val TIMEOUT_MS = 2_000L
        const val SETTLE_MS = 20L
        const val NANOS_PER_MS = 1_000_000L
    }
}
