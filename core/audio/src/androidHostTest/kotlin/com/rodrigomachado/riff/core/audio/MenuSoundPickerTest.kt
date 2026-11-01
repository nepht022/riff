package com.rodrigomachado.riff.core.audio

import com.rodrigomachado.riff.core.model.MenuSound
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class MenuSoundPickerTest {
    private val variations = mapOf(
        MenuSound.NAVIGATE to listOf(11, 12),
        MenuSound.CONFIRM to listOf(21, 22),
        MenuSound.BACK to listOf(31),
    )

    @Test
    fun eachRoleDrawsOnlyFromItsOwnVariationsAndUsesThemAll() {
        val picker = MenuSoundPicker(variations, Random(7))

        MenuSound.entries.forEach { sound ->
            val drawn = List(200) { picker.pick(sound) }.toSet()
            assertEquals(variations.getValue(sound).toSet(), drawn)
        }
    }
}
