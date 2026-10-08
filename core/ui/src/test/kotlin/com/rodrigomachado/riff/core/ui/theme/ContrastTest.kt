package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    @Test
    fun whiteOnBlackIsTwentyOne() {
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.01f)
    }

    @Test
    fun sameColorIsOne() {
        assertEquals(1f, contrastRatio(RiffColors.Paper, RiffColors.Paper), 0.001f)
    }

    @Test
    fun everyTextPairOfThePaletteMeetsAa() {
        val textPairs = mapOf(
            "papel sobre preto" to (RiffColors.Paper to RiffColors.StageBlack),
            "tinta sobre papel" to (RiffColors.Ink to RiffColors.Paper),
            "papel apagado sobre preto" to (RiffColors.PaperDim to RiffColors.StageBlack),
            "papel sobre vermelho" to (RiffColors.Paper to RiffColors.TourRed),
            "papel sobre carimbo de sucesso" to (RiffColors.Paper to RiffColors.StampSuccess),
            "papel sobre carimbo de atenção" to (RiffColors.Paper to RiffColors.StampWarning),
            "papel sobre carimbo de erro" to (RiffColors.Paper to RiffColors.StampError),
            "tinta sobre papel apagado" to (RiffColors.Ink to RiffColors.PaperDim),
        )
        textPairs.forEach { (name, pair) ->
            val ratio = contrastRatio(pair.first, pair.second)
            assertTrue("$name: $ratio < 4,5", ratio >= 4.5f)
        }
    }

    @Test
    fun redOnBlackFailsAndMustNeverCarryText() {
        assertTrue(contrastRatio(RiffColors.TourRed, RiffColors.StageBlack) < 3f)
    }
}
