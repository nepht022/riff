package com.rodrigomachado.riff.core.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Quem tira botões do controle dos menus do PC (a partida tocando, a escolha de um botão): enquanto um botão está
 * tirado, a descida dele não vira tecla nos menus. Só na thread da janela. Não é estado do Compose: ligar e desligar
 * não recompõe nada; quem lê é a coleta do controle, a cada botão que desce.
 */
class PadMenuSuspension {
    private val takers = mutableListOf<(String) -> Boolean>()

    /** A descida de [button] (texto de [Gamepad.presses]) pode virar tecla nos menus. */
    fun allows(button: String): Boolean = takers.none { it(button) }

    internal fun add(taker: (String) -> Boolean) {
        takers += taker
    }

    internal fun remove(taker: (String) -> Boolean) {
        takers -= taker
    }
}

/** ○, o botão que volta (Esc) nos menus do PC: o único que fica com eles na escolha de uma tecla (cancela). */
const val PAD_MENU_BACK = "m:1"

/** A suspensão do controle nos menus; nula no celular (lá o controle não anda nos menus). */
val LocalPadMenuSuspension = staticCompositionLocalOf<PadMenuSuspension?> { null }

/**
 * Enquanto na tela, os botões em que [takes] é verdadeiro (de fábrica, todos) não viram teclas nos menus: as descidas
 * deles são de quem chamou. Um botão segurado ao sair não dispara depois (só as descidas contam).
 */
@Composable
fun TakePadFromMenus(takes: (button: String) -> Boolean = { true }) {
    val suspension = LocalPadMenuSuspension.current ?: return
    val current by rememberUpdatedState(takes)
    DisposableEffect(suspension) {
        val taker: (String) -> Boolean = { current(it) }
        suspension.add(taker)
        onDispose { suspension.remove(taker) }
    }
}
