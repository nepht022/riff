package com.rodrigomachado.riff.core.audio

import com.rodrigomachado.riff.core.model.MenuSound

/** Sons de guitarra dos botões do menu. */
fun interface MenuSoundPlayer {
    /** Toca o som do papel [sound] em [volume] (0 a 1; 0 não toca). */
    fun play(sound: MenuSound, volume: Float)
}
