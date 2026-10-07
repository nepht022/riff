package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note
import java.security.MessageDigest

/** Acesso aos arquivos de uma pasta de música. No Android, a implementação usa SAF. */
fun interface ChartFiles {
    /** Lê o arquivo pelo nome, sem diferenciar maiúsculas, ou retorna null se ele não existir. */
    fun read(name: String): ByteArray?
}

/** Mapa carregado, com [SongInfo.offsetMs] já aplicado às notas. */
data class LoadedChart(
    val id: String,
    val format: ChartFormat,
    val info: SongInfo,
    val tracks: Map<Difficulty, List<Note>>,
)

object ChartLoader {
    fun load(files: ChartFiles): LoadedChart {
        val chartBytes = files.read("notes.chart")
        val notesBytes: ByteArray
        val format: ChartFormat
        val parsed: ParsedChart
        if (chartBytes != null) {
            notesBytes = chartBytes
            format = ChartFormat.CHART
            parsed = ChartFileParser.parse(chartBytes.decodeToString())
        } else {
            notesBytes = files.read("notes.mid") ?: throw ChartParseException("pasta sem notes.chart ou notes.mid")
            format = ChartFormat.MIDI
            parsed = MidiChartParser.parse(notesBytes)
        }
        if (parsed.tracks.isEmpty()) throw ChartParseException("nenhuma dificuldade de guitarra encontrada")

        val ini = files.read("song.ini")?.let { SongIniParser.parse(it.decodeToString()) } ?: SongInfo()
        val info = SongInfo(
            title = ini.title ?: parsed.info.title,
            artist = ini.artist ?: parsed.info.artist,
            album = ini.album ?: parsed.info.album,
            charter = ini.charter ?: parsed.info.charter,
            songLengthMs = ini.songLengthMs ?: parsed.info.songLengthMs,
            offsetMs = parsed.info.offsetMs + ini.offsetMs,
        )
        val tracks = if (info.offsetMs == 0L) {
            parsed.tracks
        } else {
            parsed.tracks.mapValues { (_, notes) -> notes.map { it.copy(timeMs = it.timeMs + info.offsetMs) } }
        }
        return LoadedChart(sha1Hex(notesBytes), format, info, tracks)
    }

    private fun sha1Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
}
