package com.rodrigomachado.riff.core.chart.match

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JaroWinklerTest {
    @Test
    fun matchesReferenceValues() {
        assertEquals(0.961, JaroWinkler.similarity("martha", "marhta"), 0.001)
        assertEquals(0.840, JaroWinkler.similarity("dwayne", "duane"), 0.001)
        assertEquals(0.813, JaroWinkler.similarity("dixon", "dicksonx"), 0.001)
    }

    @Test
    fun handlesEqualAndEmptyStrings() {
        assertEquals(1.0, JaroWinkler.similarity("riff", "riff"), 0.0)
        assertEquals(0.0, JaroWinkler.similarity("", "riff"), 0.0)
        assertEquals(0.0, JaroWinkler.similarity("abc", "xyz"), 0.0)
    }

    @Test
    fun theSharedPrefixOnlyHelpsWhenTheTextsAreAlreadySimilar() {
        // Jaro de 5/9 (abaixo de 0,7): o começo igual "abc" não pode empurrar para perto de uma sugestão.
        assertEquals(5.0 / 9, JaroWinkler.similarity("abcxxxxxx", "abcyyyyyy"), 1e-9)
    }
}
