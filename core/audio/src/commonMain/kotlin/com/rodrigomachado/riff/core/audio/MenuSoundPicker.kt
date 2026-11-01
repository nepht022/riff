package com.rodrigomachado.riff.core.audio

import com.rodrigomachado.riff.core.model.MenuSound
import kotlin.random.Random

/** Sorteia, a cada toque, uma das variações gravadas do papel do botão. */
internal class MenuSoundPicker(private val variations: Map<MenuSound, List<Int>>, private val random: Random) {
    fun pick(sound: MenuSound): Int = variations.getValue(sound).random(random)
}
