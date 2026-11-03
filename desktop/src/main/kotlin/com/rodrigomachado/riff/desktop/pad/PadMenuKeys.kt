package com.rodrigomachado.riff.desktop.pad

import com.rodrigomachado.riff.core.ui.input.PAD_MENU_BACK
import java.awt.event.KeyEvent

/**
 * O controle nos menus do PC, com botões fixos (independentes das escolhas da partida): cada botão que desce
 * ([onPress], o texto de `Gamepad.presses`) vira uma tecla em [send] (código AWT, `KeyEvent.VK_*`). O direcional é
 * ↑ → ↓ ←, ✕ e Start confirmam (Enter), ○ volta (Esc); os outros, nada. Segurar não repete: só a descida conta. Num
 * controle cru os nomes já chegam pelo layout (a tabela do DualShock 3, o chapéu como o direcional).
 */
class PadMenuKeys(private val send: (awtKeyCode: Int) -> Unit) {
    fun onPress(button: String) {
        KEYS[button]?.let(send)
    }

    private companion object {
        val KEYS: Map<String, Int> = mapOf(
            MappedButton.DPAD_UP to KeyEvent.VK_UP,
            MappedButton.DPAD_RIGHT to KeyEvent.VK_RIGHT,
            MappedButton.DPAD_DOWN to KeyEvent.VK_DOWN,
            MappedButton.DPAD_LEFT to KeyEvent.VK_LEFT,
            MappedButton.A to KeyEvent.VK_ENTER,
            MappedButton.START to KeyEvent.VK_ENTER,
        ).mapKeys { (button, _) -> "m:$button" } + (PAD_MENU_BACK to KeyEvent.VK_ESCAPE)
    }
}
