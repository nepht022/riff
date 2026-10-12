package com.rodrigomachado.riff.core.chart.sng

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SngPackageTest {
    private val notes = "[Song]\n{\n}\n".toByteArray()
    private val audio = ByteArray(1_000) { (it % 251).toByte() }
    private val bytes = SngWriter.write(
        mapOf("name" to "Música", "artist" to "Banda", "delay" to "120", "song_length" to "200000"),
        mapOf("notes.chart" to notes, "song.opus" to audio),
    )

    @Test
    fun readsTheIndexTheMetadataAndUnmasksEveryFile() {
        SngPackage.open(ByteArrayRandomRead(bytes)).use { sng ->
            assertEquals(listOf("notes.chart", "song.opus"), sng.files.map { it.name })
            assertArrayEquals(notes, sng.read("notes.chart", maxBytes = 1_000_000))
            assertArrayEquals(audio, sng.read("song.opus", maxBytes = 1_000_000))
            assertEquals("Música", sng.songInfo().title)
            assertEquals(120L, sng.songInfo().offsetMs)
        }
    }

    @Test
    fun aFileOpensForReadingAtAnyPosition() {
        SngPackage.open(ByteArrayRandomRead(bytes)).use { sng ->
            val file = sng.open("song.opus")
            val buffer = ByteArray(10)
            assertEquals(10, file.read(500, buffer, 0, 10))
            assertArrayEquals(audio.copyOfRange(500, 510), buffer)
            assertEquals(1_000L, file.size)
            assertEquals(-1, file.read(1_000, buffer, 0, 10))
        }
    }

    @Test
    fun aWrongHeaderIsAnInvalidPackage() {
        assertThrows<InvalidSngException> { SngPackage.open(ByteArrayRandomRead("NOTSNG0000".toByteArray())) }
    }

    @Test
    fun aTruncatedPackageIsInvalidNotACrash() {
        assertThrows<InvalidSngException> { SngPackage.open(ByteArrayRandomRead(bytes.copyOf(bytes.size - 10))) }
    }

    @Test
    fun hugeCountsAreRejectedBeforeAllocating() {
        val forged = bytes.copyOf().also {
            // metadataCount (posição 34) com um valor absurdo
            java.nio.ByteBuffer.wrap(it, 34, 8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(Long.MAX_VALUE)
        }
        assertThrows<InvalidSngException> { SngPackage.open(ByteArrayRandomRead(forged)) }
    }

    @Test
    fun aFileLargerThanTheLimitIsRefused() {
        SngPackage.open(ByteArrayRandomRead(bytes)).use { sng ->
            assertThrows<java.io.IOException> { sng.read("song.opus", maxBytes = 10) }
        }
    }

    @Test
    fun theIndexComesFromJustTheBeginningOfTheFile() {
        val result = SngIndex.parse(bytes.copyOf(200))
        assertTrue(result is SngIndex.Result.Complete)
        assertEquals(listOf("notes.chart", "song.opus"), (result as SngIndex.Result.Complete).files.map { it.name })
        assertTrue(SngIndex.parse(bytes.copyOf(30)) is SngIndex.Result.NeedsMore)
    }
}
