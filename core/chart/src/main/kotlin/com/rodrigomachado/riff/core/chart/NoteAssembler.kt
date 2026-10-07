package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.model.Note
import kotlin.math.roundToLong

/** Como o arquivo força (ou não) um acorde a ser HOPO. */
internal enum class Forcing {
    NONE,

    /** .chart: inverte o HOPO natural. */
    FLIP,

    /** MIDI: força HOPO. */
    HOPO,

    /** MIDI: força palhetada. */
    STRUM,
}

/** Notas que começam no mesmo tick (acorde ou nota simples), ainda em ticks. */
internal data class RawChord(
    val tick: Long,
    val lengthsByLane: Map<Int, Long>,
    val forcing: Forcing = Forcing.NONE,
    val isTap: Boolean = false,
)

/** Converte acordes em ticks em [Note]s em ms, calculando HOPO natural e forçado. */
internal object NoteAssembler {
    fun assemble(chords: List<RawChord>, tempoMap: TempoMap, hopoThresholdTicks: Long): List<Note> {
        val notes = ArrayList<Note>()
        var previous: RawChord? = null
        for (chord in chords.filter { it.lengthsByLane.isNotEmpty() }.sortedBy { it.tick }) {
            val isHopo = chord.isTap || resolveHopo(chord, previous, hopoThresholdTicks)
            val startMs = tempoMap.tickToMs(chord.tick)
            for ((lane, lengthTicks) in chord.lengthsByLane.toSortedMap()) {
                val sustainMs = if (lengthTicks > 0) tempoMap.tickToMs(chord.tick + lengthTicks) - startMs else 0.0
                notes += Note(startMs.roundToLong(), lane, sustainMs.roundToLong(), isHopo)
            }
            previous = chord
        }
        return notes
    }

    private fun resolveHopo(chord: RawChord, previous: RawChord?, thresholdTicks: Long): Boolean {
        val natural = previous != null &&
            chord.lengthsByLane.size == 1 &&
            chord.tick - previous.tick <= thresholdTicks &&
            chord.lengthsByLane.keys.single() !in previous.lengthsByLane.keys
        return when (chord.forcing) {
            Forcing.NONE -> natural
            Forcing.FLIP -> !natural
            Forcing.HOPO -> true
            Forcing.STRUM -> false
        }
    }
}
