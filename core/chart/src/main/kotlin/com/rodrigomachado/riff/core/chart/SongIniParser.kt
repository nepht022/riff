package com.rodrigomachado.riff.core.chart

import kotlin.math.roundToLong

/** Lê o song.ini do Clone Hero (seção [song], chaves sem diferenciar maiúsculas). */
object SongIniParser {
    private const val MS_PER_SECOND = 1_000
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
            // Como no YARG: o delay (ms) vale se for diferente de 0; senão, o delay_seconds (s).
            offsetMs = value("delay")?.toLongOrNull()?.takeIf { it != 0L }
                ?: value("delay_seconds")?.toDoubleOrNull()?.let { (it * MS_PER_SECOND).roundToLong() }
                ?: 0,
        )
    }
}
