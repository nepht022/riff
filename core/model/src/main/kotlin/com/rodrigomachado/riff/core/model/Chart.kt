package com.rodrigomachado.riff.core.model

enum class ChartFormat { CHART, MIDI }

enum class Difficulty { EASY, MEDIUM, HARD, EXPERT }

/** Mapa importado. [id] é o SHA-1 do arquivo de notas, então reimportar o mesmo mapa não duplica. */
data class Chart(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val charter: String?,
    val durationMs: Long?,
    val folderUri: String,
    val format: ChartFormat,
    val difficulties: Set<Difficulty>,
    /** A pasta do mapa já traz a guitarra à parte (`guitar.*` e o resto da banda). */
    val separateGuitar: Boolean = false,
)
