package com.rodrigomachado.riff.core.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RoomCodesTest {
    @Test
    fun randomUsesOnlyTheAlphabet() {
        val random = Random(42)
        val codes = List(10_000) { RoomCodes.random(random) }

        codes.forEach { code ->
            assertEquals(4, code.length)
            assertTrue(code, code.all { it in RoomCodes.ALPHABET })
            assertTrue(code, code.none { it in "ILO" })
        }
        // todas as 23 letras aparecem
        assertEquals(RoomCodes.ALPHABET.toSet(), codes.joinToString("").toSet())
    }

    @Test
    fun normalizeUppercasesAndTrims() {
        assertEquals("ABCD", RoomCodes.normalize(" abcd "))
        assertEquals("ABCD", RoomCodes.normalize("ab cd"))
        assertEquals("ZZZZ", RoomCodes.normalize("ZZZZ"))
    }

    @Test
    fun normalizeRefusesAmbiguousAndWrongLength() {
        listOf("ABCO", "ABCI", "ABCL", "AB1D", "ABC", "ABCDE", "", "AB-D").forEach {
            assertNull(it, RoomCodes.normalize(it))
        }
    }
}
