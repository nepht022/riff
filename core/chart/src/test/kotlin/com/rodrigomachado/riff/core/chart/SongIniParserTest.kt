package com.rodrigomachado.riff.core.chart

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SongIniParserTest {
    @Test
    fun readsKnownKeys() {
        val ini = """
            [song]
            name = Minha Música
            artist = Banda
            album = Disco
            charter = Fulano
            song_length = 215000
            delay = -120
        """.trimIndent()
        assertEquals(SongInfo("Minha Música", "Banda", "Disco", "Fulano", 215_000, -120), SongIniParser.parse(ini))
    }

    @Test
    fun ignoresCaseOfSectionAndKeysAndUsesFretsAsCharter() {
        val ini = "[Song]\nNAME = Música\nArtist = Banda\nfrets = Beltrano"
        assertEquals(SongInfo(title = "Música", artist = "Banda", charter = "Beltrano"), SongIniParser.parse(ini))
    }

    @Test
    fun ignoresOtherSectionsCommentsAndEmptyValues() {
        val ini = "[other]\nname = Errado\n[song]\n; name = Comentário\nname = Certo\nartist =\nsong_length = 0"
        assertEquals(SongInfo(title = "Certo"), SongIniParser.parse(ini))
    }

    @Test
    fun handlesBomAndCrlf() {
        assertEquals(SongInfo(title = "X"), SongIniParser.parse("﻿[song]\r\nname = X\r\n"))
    }
}
