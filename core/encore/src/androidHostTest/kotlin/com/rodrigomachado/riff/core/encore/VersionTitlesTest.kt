package com.rodrigomachado.riff.core.encore

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionTitlesTest {
    @Test
    fun coversRemixesLiveAndMedleysAreVersions() {
        listOf(
            "Enter Sandman (Metallica cover)",
            "Fade to Black (Live at Tushino)",
            "Black Album Medley",
            "Numb (Remix)",
            "Céu Azul (Ao Vivo)",
            "Wonderwall - Acoustic",
            "Mashup of Hits",
        ).forEach { assertEquals(it, true, VersionTitles.isVersion(it)) }
    }

    @Test
    fun originalsAndRemastersAreNot() {
        listOf(
            "Battery",
            "Smells Like Teen Spirit",
            "Bohemian Rhapsody - Remastered 2011",
            "Livewire",
            "Discover",
            "Alive",
        ).forEach { assertEquals(it, false, VersionTitles.isVersion(it)) }
    }
}
