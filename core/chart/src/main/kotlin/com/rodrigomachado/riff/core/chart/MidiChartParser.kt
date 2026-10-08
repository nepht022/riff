package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note

/** Extrai as notas da trilha PART GUITAR de um notes.mid no padrão Rock Band / Clone Hero. */
object MidiChartParser {
    private const val GUITAR_TRACK = "PART GUITAR"
    private val BASE_KEYS = mapOf(
        Difficulty.EASY to 60,
        Difficulty.MEDIUM to 72,
        Difficulty.HARD to 84,
        Difficulty.EXPERT to 96,
    )
    private const val FORCE_HOPO_OFFSET = 5
    private const val FORCE_STRUM_OFFSET = 6

    // Limite do HOPO natural do Clone Hero para MIDI: 170/480 de semínima.
    private const val HOPO_THRESHOLD_NUMERATOR = 170L
    private const val HOPO_THRESHOLD_DENOMINATOR = 480L

    // Notas mais curtas que 1/3 de semínima não são sustain.
    private const val SUSTAIN_CUTOFF_DIVISOR = 3L

    fun parse(bytes: ByteArray): ParsedChart {
        val midi = MidiReader.read(bytes)
        val tempos = midi.tracks
            .flatMap { it.events }
            .filterIsInstance<MidiReader.Tempo>()
            .map { TempoMap.TempoChange(it.tick, it.microsPerQuarter) }
        val tempoMap = TempoMap(midi.ticksPerQuarter, tempos)
        val guitar = midi.tracks.firstOrNull { it.name?.trim() == GUITAR_TRACK }
            ?: throw ChartParseException("trilha $GUITAR_TRACK não encontrada")
        val spans = noteSpans(guitar.events)
        val resolution = midi.ticksPerQuarter.toLong()
        val threshold = resolution * HOPO_THRESHOLD_NUMERATOR / HOPO_THRESHOLD_DENOMINATOR
        val sustainCutoff = resolution / SUSTAIN_CUTOFF_DIVISOR
        val tracks = BASE_KEYS.mapNotNull { (difficulty, base) ->
            val notes = NoteAssembler.assemble(chordsFor(spans, base, sustainCutoff), tempoMap, threshold)
            if (notes.isEmpty()) null else difficulty to notes
        }.toMap()
        return ParsedChart(SongInfo(), tracks)
    }

    private data class Span(val key: Int, val startTick: Long, val endTick: Long) {
        fun covers(tick: Long) = tick == startTick || tick in startTick until endTick
    }

    private fun noteSpans(events: List<MidiReader.MidiEvent>): List<Span> {
        val open = mutableMapOf<Int, Long>()
        // Teclas cuja nota anterior foi fechada por um note-on no tick indicado. Um note-off nesse mesmo
        // tick pertence à nota anterior (alguns exportadores gravam o note-on da próxima antes do note-off).
        val retriggeredAt = mutableMapOf<Int, Long>()
        val spans = mutableListOf<Span>()
        for (event in events) {
            when (event) {
                is MidiReader.NoteOn -> {
                    retriggeredAt.remove(event.key)
                    open.remove(event.key)?.let {
                        spans += Span(event.key, it, event.tick)
                        retriggeredAt[event.key] = event.tick
                    }
                    open[event.key] = event.tick
                }

                is MidiReader.NoteOff -> {
                    if (retriggeredAt.remove(event.key) == event.tick) continue
                    open.remove(event.key)?.let { spans += Span(event.key, it, event.tick) }
                }

                is MidiReader.Tempo -> Unit
            }
        }
        open.forEach { (key, start) -> spans += Span(key, start, start) }
        return spans
    }

    private fun chordsFor(spans: List<Span>, base: Int, sustainCutoff: Long): List<RawChord> {
        val hopoMarkers = spans.filter { it.key == base + FORCE_HOPO_OFFSET }
        val strumMarkers = spans.filter { it.key == base + FORCE_STRUM_OFFSET }
        return spans
            .filter { it.key in base until base + Note.LANE_COUNT }
            .groupBy { it.startTick }
            .map { (tick, group) ->
                val lengths = group.associate { span ->
                    val length = span.endTick - span.startTick
                    (span.key - base) to if (length > sustainCutoff) length else 0L
                }
                val forcing = when {
                    hopoMarkers.any { it.covers(tick) } -> Forcing.HOPO
                    strumMarkers.any { it.covers(tick) } -> Forcing.STRUM
                    else -> Forcing.NONE
                }
                RawChord(tick, lengths, forcing)
            }
    }
}
