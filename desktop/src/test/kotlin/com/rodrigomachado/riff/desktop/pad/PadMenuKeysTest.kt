package com.rodrigomachado.riff.desktop.pad

import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.event.KeyEvent

/** Os botões fixos do controle nos menus: o direcional vira as setas, ✕ e Start o Enter, ○ o Esc; o resto, nada. */
class PadMenuKeysTest {
    private val sent = mutableListOf<Int>()
    private val keys = PadMenuKeys { sent += it }

    @Test
    fun dpadAndCrossMapToArrowsAndEnter() {
        listOf("m:11", "m:12", "m:13", "m:14", "m:0", "m:7").forEach(keys::onPress)

        assertEquals(
            listOf(
                KeyEvent.VK_UP,
                KeyEvent.VK_RIGHT,
                KeyEvent.VK_DOWN,
                KeyEvent.VK_LEFT,
                KeyEvent.VK_ENTER,
                KeyEvent.VK_ENTER,
            ),
            sent,
        )
    }

    @Test
    fun circleIsEscape() {
        keys.onPress("m:1")

        assertEquals(listOf(KeyEvent.VK_ESCAPE), sent)
    }

    @Test
    fun otherButtonsSendNothing() {
        // △, □, R1, Select, um botão cru fora da tabela e o chapéu (que no cru já chega como m:11/m:13).
        listOf("m:3", "m:2", "m:5", "m:6", "r:20", "h:up", "").forEach(keys::onPress)

        assertEquals(emptyList<Int>(), sent)
    }
}
