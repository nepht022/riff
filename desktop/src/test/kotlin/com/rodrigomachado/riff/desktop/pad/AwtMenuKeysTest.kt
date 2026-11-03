package com.rodrigomachado.riff.desktop.pad

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.GraphicsEnvironment
import java.awt.event.KeyEvent
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities

/**
 * O caminho de verdade das teclas do controle: uma janela do Compose aberta e focada recebe a tecla entregue pelo
 * AWT ([pressMenuKey]) como uma do teclado, descida e subida. Sem tela (o CI) ou sem o foco do sistema (outra janela
 * na frente), o teste não roda.
 */
class AwtMenuKeysTest {
    @Test
    fun aKeyPressedOnTheFocusedWindowReachesCompose() {
        assumeFalse("sem tela", GraphicsEnvironment.isHeadless())
        val events = CopyOnWriteArrayList<Pair<Key, KeyEventType>>()
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow().apply {
                setSize(WINDOW_SIZE, WINDOW_SIZE)
                setContent {
                    val focus = remember { FocusRequester() }
                    Box(
                        Modifier.fillMaxSize().focusRequester(focus).focusable().onKeyEvent {
                            events += it.key to it.type
                            true
                        },
                    )
                    LaunchedEffect(Unit) { focus.requestFocus() }
                }
                isVisible = true
                // Sem `requestFocus()` da janela: o foco do teclado ficaria nela, e não no painel do Compose.
                toFront()
            }
        }
        try {
            val focused = waitFor { window.isFocused && window.focusOwner != null }
            assumeTrue("a janela não ganhou o foco do sistema", focused)
            Thread.sleep(SETTLE_MS) // o primeiro quadro (e o foco do Box)

            window.pressMenuKey(KeyEvent.VK_DOWN)

            waitFor { events.size >= 2 }
            assertEquals(
                listOf(Key.DirectionDown to KeyEventType.KeyDown, Key.DirectionDown to KeyEventType.KeyUp),
                events.toList(),
            )
        } finally {
            SwingUtilities.invokeAndWait { window.dispose() }
        }
    }

    private fun waitFor(done: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!done()) {
            if (System.currentTimeMillis() > deadline) return false
            Thread.sleep(POLL_MS)
        }
        return true
    }

    private companion object {
        const val WINDOW_SIZE = 200
        const val TIMEOUT_MS = 5_000L
        const val SETTLE_MS = 500L
        const val POLL_MS = 10L
    }
}
