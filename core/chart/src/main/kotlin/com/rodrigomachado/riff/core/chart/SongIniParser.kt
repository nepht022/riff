package com.rodrigomachado.riff.core.chart

/** Lê o song.ini do Clone Hero (seção [song], chaves sem diferenciar maiúsculas). */
object SongIniParser {
    fun parse(text: String): SongInfo {
        val values = mutableMapOf<String, String>()
        var inSong = false
        for (rawLine in text.removePrefix("﻿").lines()) {
            val line = rawLine.trim()
            when {
                line.startsWith("[") -> inSong = line.equals("[song]", ignoreCase = true)

                line.startsWith(";") || line.startsWith("#") -> Unit

                inSong && '=' in line ->
                    values[line.substringBefore('=').trim().lowercase()] = line.substringAfter('=').trim()
            }
        }
        fun value(key: String) = values[key]?.takeIf { it.isNotEmpty() }
        return SongInfo(
            title = value("name"),
            artist = value("artist"),
            album = value("album"),
            charter = value("charter") ?: value("frets"),
            songLengthMs = value("song_length")?.toLongOrNull()?.takeIf { it > 0 },
            offsetMs = value("delay")?.toLongOrNull() ?: 0,
        )
    }
}
