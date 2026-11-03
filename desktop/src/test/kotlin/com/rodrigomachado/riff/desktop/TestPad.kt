package com.rodrigomachado.riff.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.rodrigomachado.riff.core.model.Controls
import com.rodrigomachado.riff.desktop.pad.PadSnapshot
import com.rodrigomachado.riff.desktop.pad.PadSource
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities

/**
 * Um controle mapeado falso para o app de teste: [press] desce e solta um botão do layout (`m:N`), cada mudança
 * vista por duas leituras da thread `riff-pad` antes de voltar.
 */
internal class TestPad : PadSource {
    @Volatile private var held: Set<Int> = emptySet()

    @Volatile private var polls = 0L

    override fun init() = true

    override fun poll(): PadSnapshot = PadSnapshot(raw = emptySet(), layout = held).also { polls++ }

    override fun close() = Unit

    fun press(button: Int) {
        hold(button)
        release(button)
    }

    fun hold(button: Int) {
        held = held + button
        awaitPolls()
    }

    fun release(button: Int) {
        held = held - button
        awaitPolls()
    }

    private fun awaitPolls() {
        val target = polls + 2
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (polls < target) {
            check(System.currentTimeMillis() < deadline) { "a thread do controle não leu" }
            Thread.sleep(1)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}

/**
 * As teclas que o controle mandou aos menus ([sent], códigos AWT), entregues à raiz de cima (o diálogo aberto, se
 * houver) na thread da janela, como a tecla de verdade chega à janela no app.
 */
@OptIn(ExperimentalTestApi::class)
internal class MenuKeyLog(private val composeRule: ComposeContentTestRule) {
    val sent: MutableList<Int> = CopyOnWriteArrayList()

    val send: (Int) -> Unit = { code ->
        SwingUtilities.invokeLater {
            composeRule.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(Key(Controls.desktopKey(code))) }
            sent += code
        }
    }
}
