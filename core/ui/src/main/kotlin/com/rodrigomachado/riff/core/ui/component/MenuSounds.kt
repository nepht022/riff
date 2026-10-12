package com.rodrigomachado.riff.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.rodrigomachado.riff.core.model.MenuSound

/** Toca o som de guitarra de um papel de botão; o app fornece (com o volume e a preferência do jogador). */
val LocalMenuSound = staticCompositionLocalOf<(MenuSound) -> Unit> { {} }

/** [onClick] precedido do som do papel [sound]; sem papel, só [onClick] (botões que ficam em silêncio). */
@Composable
fun withMenuSound(sound: MenuSound?, onClick: () -> Unit): () -> Unit {
    if (sound == null) return onClick
    val play = LocalMenuSound.current
    return {
        play(sound)
        onClick()
    }
}

/** Mesmo que [withMenuSound], para ações que recebem um valor (o mapa ou a dificuldade escolhidos). */
@Composable
fun <T> withMenuSound(sound: MenuSound, onClick: (T) -> Unit): (T) -> Unit {
    val play = LocalMenuSound.current
    return {
        play(sound)
        onClick(it)
    }
}
