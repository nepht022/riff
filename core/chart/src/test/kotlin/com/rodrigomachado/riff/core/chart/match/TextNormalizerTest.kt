package com.rodrigomachado.riff.core.chart.match

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextNormalizerTest {
    private fun n(text: String) = TextNormalizer.normalize(text)

    @Test
    fun removesRemasterAndLiveSuffixes() {
        assertEquals("bohemian rhapsody", n("Bohemian Rhapsody - Remastered 2011"))
        assertEquals("tempo perdido", n("Tempo Perdido - Live"))
        assertEquals("tempo perdido", n("Tempo Perdido (Ao Vivo)"))
    }

    @Test
    fun removesFeaturingWithOrWithoutParentheses() {
        assertEquals("umbrella", n("Umbrella (feat. JAY-Z)"))
        assertEquals("song", n("Song feat. Someone Else"))
        assertEquals("song", n("Song [ft. Someone]"))
    }

    @Test
    fun stripsAccentsCaseAndPunctuation() {
        assertEquals("acao", n("Ação!"))
        assertEquals("ac dc", n("AC/DC"))
        assertEquals("guns n roses", n("Guns N' Roses"))
        assertEquals("rock and roll", n("Rock & Roll"))
    }

    @Test
    fun keepsWordsThatOnlyLookLikeQualifiers() {
        assertEquals("live and let die", n("Live and Let Die"))
    }

    @Test
    fun keepsLettersFromAnyScript() {
        assertEquals("米津玄師", n("米津玄師"))
        assertEquals("кино", n("Кино"))
    }

    @Test
    fun typographicApostrophesAreRemovedLikeStraightOnes() {
        assertEquals(n("Don't Stop Me Now"), n("Don’t Stop Me Now"))
        assertEquals("dont stop me now", n("Don‘t Stop Me Now"))
    }
}
