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
        val palette = setOf(RiffColors.TourRed, RiffColors.StampWarning, RiffColors.StampSuccess)
        assertEquals(palette, ids.map(::thumbColor).toSet())
        palette.forEach {
            // As iniciais em papel se leem sobre qualquer uma delas.
            assertTrue(contrastRatio(RiffColors.Paper, it) >= 4.5f)
            // E o quadrado não some no fundo do palco nem no cartão de papel (a tinta quase sumia no palco).
            assertTrue(contrastRatio(it, RiffColors.StageBlack) >= 2.5f)
            assertTrue(contrastRatio(it, RiffColors.Paper) >= 4.5f)
        }
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
