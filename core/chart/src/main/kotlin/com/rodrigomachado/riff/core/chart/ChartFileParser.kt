package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note
import kotlin.math.roundToLong

/** Lê o formato .chart (texto) do Clone Hero e do Moonscraper. */
object ChartFileParser {
    private val TRACK_SECTIONS = mapOf(
        "EasySingle" to Difficulty.EASY,
        "MediumSingle" to Difficulty.MEDIUM,
        "HardSingle" to Difficulty.HARD,
        "ExpertSingle" to Difficulty.EXPERT,
    )
    private const val DEFAULT_RESOLUTION = 192
    private const val FORCED_FLAG = 5
    private const val TAP_FLAG = 6

    // Limite do HOPO natural do Clone Hero: 65/192 de semínima.
    private const val HOPO_THRESHOLD_NUMERATOR = 65L
    private const val HOPO_THRESHOLD_DENOMINATOR = 192L
    private val WHITESPACE = Regex("\\s+")

    fun parse(text: String): ParsedChart {
        val sections = readSections(text)
        val song = sections["Song"].orEmpty().associate { it.key to it.value.removeSurrounding("\"") }
        val resolution = song["Resolution"]?.toIntOrNull() ?: DEFAULT_RESOLUTION
        if (resolution <= 0) throw ChartParseException("resolução inválida: $resolution")
        val tempoMap = TempoMap(resolution, readTempos(sections["SyncTrack"].orEmpty()))
        val threshold = resolution * HOPO_THRESHOLD_NUMERATOR / HOPO_THRESHOLD_DENOMINATOR
        val tracks = TRACK_SECTIONS.mapNotNull { (name, difficulty) ->
            val entries = sections[name] ?: return@mapNotNull null
            val notes = NoteAssembler.assemble(readChords(entries), tempoMap, threshold)
            if (notes.isEmpty()) null else difficulty to notes
        }.toMap()
        val info = SongInfo(
            title = song["Name"],
            artist = song["Artist"],
            album = song["Album"],
            charter = song["Charter"],
            offsetMs = song["Offset"]?.toDoubleOrNull()?.let { (it * 1000).roundToLong() } ?: 0,
        )
        return ParsedChart(info, tracks)
    }

    private data class Entry(val line: Int, val key: String, val value: String)

    private fun readSections(text: String): Map<String, List<Entry>> {
        val sections = mutableMapOf<String, MutableList<Entry>>()
        var current: MutableList<Entry>? = null
        text.removePrefix("﻿").lines().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() || line == "{" -> Unit

                line == "}" -> current = null

                line.startsWith("[") && line.endsWith("]") ->
                    current = sections.getOrPut(line.substring(1, line.length - 1)) { mutableListOf() }

                else -> {
                    val separator = line.indexOf('=')
                    if (separator > 0) {
                        current?.add(
                            Entry(index + 1, line.substring(0, separator).trim(), line.substring(separator + 1).trim()),
                        )
                    }
                }
            }
        }
        return sections
    }

    private fun readTempos(entries: List<Entry>): List<TempoMap.TempoChange> = entries.mapNotNull { entry ->
        val parts = entry.value.split(WHITESPACE)
        if (parts.first() != "B") return@mapNotNull null
        val tick = entry.key.toLongOrNull()?.takeIf { it >= 0 }
            ?: throw ChartParseException("tick inválido: ${entry.key}", entry.line)
        val milliBpm = parts.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0 }
            ?: throw ChartParseException("andamento inválido: ${entry.value}", entry.line)
        TempoMap.TempoChange(tick, TempoMap.microsPerQuarterFromMilliBpm(milliBpm))
    }

    private class ChordBuilder {
        val lengths = mutableMapOf<Int, Long>()
        var forced = false
        var tap = false
    }

    private fun readChords(entries: List<Entry>): List<RawChord> {
        val byTick = sortedMapOf<Long, ChordBuilder>()
        for (entry in entries) {
            val parts = entry.value.split(WHITESPACE)
            if (parts.first() != "N") continue
            fun invalid(): Nothing = throw ChartParseException(
                "nota inválida: ${entry.key} = ${entry.value}",
                entry.line,
            )
            val tick = entry.key.toLongOrNull()?.takeIf { it >= 0 } ?: invalid()
            val fret = parts.getOrNull(1)?.toIntOrNull() ?: invalid()
            val length = parts.getOrNull(2)?.toLongOrNull()?.takeIf { it >= 0 } ?: invalid()
            val chord = byTick.getOrPut(tick) { ChordBuilder() }
            when (fret) {
                in 0 until Note.LANE_COUNT -> chord.lengths[fret] = length
                FORCED_FLAG -> chord.forced = true
                TAP_FLAG -> chord.tap = true
                else -> Unit // 7 = nota aberta, fora do MVP
            }
        }
        return byTick.map { (tick, chord) ->
            RawChord(tick, chord.lengths, if (chord.forced) Forcing.FLIP else Forcing.NONE, chord.tap)
        }
    }
}
