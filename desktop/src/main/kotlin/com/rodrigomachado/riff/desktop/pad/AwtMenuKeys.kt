package com.rodrigomachado.riff.desktop.pad

import java.awt.EventQueue
import java.awt.Window
import java.awt.event.KeyEvent

/**
 * Entrega a tecla [awtKeyCode] (`KeyEvent.VK_*`) à janela como uma de verdade: a descida e a subida (`KEY_PRESSED`,
 * `KEY_RELEASED`) a quem tem o foco do teclado nela (o painel do Compose, que a passa ao diálogo aberto), na thread
 * do AWT e fora da coleta que a pediu. Com a janela sem foco, nada.
 */
fun Window.pressMenuKey(awtKeyCode: Int) = EventQueue.invokeLater {
    val target = focusOwner ?: return@invokeLater
    val at = System.currentTimeMillis()
    for (id in listOf(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED)) {
        target.dispatchEvent(KeyEvent(target, id, at, 0, awtKeyCode, KeyEvent.CHAR_UNDEFINED))
    }
}
