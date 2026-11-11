package com.rodrigomachado.riff.core.ui.component

import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartThumbLookTest {
    @Test
    fun initialsColorIsStablePerChart() {
        val ids = (1..60).map { "chart-$it" }
        // A mesma cor para o mesmo mapa, sempre (a lista rola, a tela volta, o app reabre).
        ids.forEach { assertEquals(thumbColor(it), thumbColor(it)) }
        assertEquals(thumbColor("abc"), thumbColor(String(charArrayOf('a', 'b', 'c'))))
        // Só as três cores do tema, e as três aparecem entre mapas diferentes.
        val palette = setOf(RiffColors.TourRed, RiffColors.Ink, RiffColors.StampWarning)
        assertEquals(palette, ids.map(::thumbColor).toSet())
        // As iniciais em papel se leem sobre qualquer uma delas.
        palette.forEach { assertTrue(contrastRatio(RiffColors.Paper, it) >= 4.5f) }
    }

    @Test
    fun initialsTakeUpToTwoWords() {
        assertEquals("TF", thumbInitials("Through the Fire and Flames"))
        assertEquals("BB", thumbInitials("back in black"))
        assertEquals("AS", thumbInitials("Ace of Spades"))
        assertEquals("T", thumbInitials("The Trooper"))
        assertEquals("TA", thumbInitials("The A"))
        assertEquals("T", thumbInitials("Thunderstruck"))
        assertEquals("ÁC", thumbInitials("  (Ácido)  - Cósmico!"))
        assertEquals("?", thumbInitials(""))
        assertEquals("?", thumbInitials("  --  "))
    }
}
