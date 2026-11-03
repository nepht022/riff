package com.rodrigomachado.riff.desktop.pad

import com.rodrigomachado.riff.core.ui.input.Gamepad
import com.rodrigomachado.riff.core.ui.input.PadEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.thread

/**
 * O controle do PC, lido numa thread própria (`riff-pad`, daemon) a cada ~1 ms: a mesma thread inicia, lê e encerra a
 * [source] (o GLFW exige). Só as mudanças viram [events], com o instante [clock] da leitura em que apareceram. Sem o
 * GLFW (nativo ausente ou `glfwInit` falso), [connected] fica falso para sempre e o jogo segue só com o teclado.
 */
class GlfwGamepad(
    /** De onde vem o controle (nos testes, uma fonte falsa: o CI não tem tela para o GLFW). */
    private val source: PadSource = GlfwPadSource(),
    /** O relógio dos eventos, o mesmo da partida. */
    private val clock: () -> Long = System::nanoTime,
) : Gamepad,
    AutoCloseable {
    private val mutableEvents = MutableSharedFlow<PadEvent>(extraBufferCapacity = EVENT_BUFFER)
    private val mutableConnected = MutableStateFlow(false)
    override val events: SharedFlow<PadEvent> = mutableEvents.asSharedFlow()
    override val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()

    @Volatile private var running = true
    private val reader = thread(isDaemon = true, name = "riff-pad") { read() }

    /** A thread de leitura ainda está viva (nos testes). */
    internal val isReading: Boolean get() = reader.isAlive

    private fun read() {
        if (!started()) return
        try {
            val mapper = PadMapper()
            while (running) {
                val snapshot = source.poll()
                val at = clock()
                // Sumiu: desconectado antes das cores soltas, para a partida pausar antes de soltá-las.
                if (snapshot == null) mutableConnected.value = false
                mapper.changes(snapshot, at).forEach { mutableEvents.tryEmit(it) }
                mutableConnected.value = snapshot != null
                Thread.sleep(POLL_MS)
            }
        } catch (_: InterruptedException) {
            // close(): a leitura acaba aqui.
        } finally {
            mutableConnected.value = false
            source.close()
        }
    }

    /** O nativo do GLFW que não carrega é o mesmo que um PC sem controle. */
    private fun started(): Boolean = try {
        source.init()
    } catch (_: LinkageError) {
        false
    }

    /** Para a leitura e espera a thread encerrar o GLFW (até [JOIN_MS]: fechar o jogo nunca trava aqui). */
    override fun close() {
        running = false
        reader.interrupt()
        reader.join(JOIN_MS)
    }

    private companion object {
        const val EVENT_BUFFER = 64
        const val POLL_MS = 1L
        const val JOIN_MS = 1_000L
    }
}
