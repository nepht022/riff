package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note

/** Metadados de uma música, vindos do song.ini ou da seção [Song] do .chart. */
data class SongInfo(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val charter: String? = null,
    val songLengthMs: Long? = null,
    /** Deslocamento somado ao tempo de todas as notas; positivo atrasa as notas em relação ao áudio. */
    val offsetMs: Long = 0,
)

/** Resultado de um parser. As notas ainda não incluem [SongInfo.offsetMs]. */
data class ParsedChart(val info: SongInfo, val tracks: Map<Difficulty, List<Note>>)

class ChartParseException(message: String, val line: Int? = null, cause: Throwable? = null) :
    Exception(if (line != null) "linha $line: $message" else message, cause)
