package com.rodrigomachado.riff.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rodrigomachado.riff.core.model.MenuSound

/** Toca o som de guitarra de um papel de botão; o app fornece (com o volume e a preferência do jogador). */
val LocalMenuSound = staticCompositionLocalOf<(MenuSound) -> Unit> { {} }

/**
 * O som dos botões desta tela. Só toca com a tela em primeiro plano: durante uma animação de navegação o toque é
 * descartado (`dropUnlessResumed`), e um som sem ação faria parecer que o botão funcionou.
 */
@Composable
fun rememberMenuSound(): (MenuSound) -> Unit {
    val play = LocalMenuSound.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return { sound -> if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) play(sound) }
}

/** [onClick] precedido do som do papel [sound]; sem papel, só [onClick] (botões que ficam em silêncio). */
@Composable
fun withMenuSound(sound: MenuSound?, onClick: () -> Unit): () -> Unit {
    if (sound == null) return onClick
    val play = rememberMenuSound()
    return {
        play(sound)
        onClick()
    }
}

/** Mesmo que [withMenuSound], para ações que recebem um valor (ligar ou desligar um interruptor). */
@Composable
fun <T> withMenuSound(sound: MenuSound, onClick: (T) -> Unit): (T) -> Unit {
    val play = rememberMenuSound()
    return {
        play(sound)
        onClick(it)
    }
}
