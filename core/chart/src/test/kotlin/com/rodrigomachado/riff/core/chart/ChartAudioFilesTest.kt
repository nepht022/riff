package com.rodrigomachado.riff.core.chart

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChartAudioFilesTest {
    @Test
    fun separateGuitarNeedsGuitarAndAnotherTrack() {
        assertEquals(true, ChartAudioFiles.hasSeparateGuitar(listOf("notes.chart", "guitar.ogg", "song.opus")))
        assertEquals(false, ChartAudioFiles.hasSeparateGuitar(listOf("notes.chart", "song.opus")))
        assertEquals(false, ChartAudioFiles.hasSeparateGuitar(listOf("guitar.ogg")))
        assertEquals("Song.OGG", ChartAudioFiles.find(listOf("x.txt", "Song.OGG"), "song"))
    }
}
