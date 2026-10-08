package com.rodrigomachado.riff.core.chart

/** Converte ticks em milissegundos levando em conta as mudanças de andamento. */
class TempoMap(private val resolution: Int, changes: List<TempoChange>) {

    /** Mudança de andamento no [tick], em microssegundos por semínima (a unidade do MIDI). */
    data class TempoChange(val tick: Long, val microsPerQuarter: Long)

    private class Segment(val startTick: Long, val startMs: Double, val microsPerQuarter: Long)

    private val segments: List<Segment>

    init {
        require(resolution > 0) { "resolução precisa ser positiva: $resolution" }
        val sorted = changes.sortedBy { it.tick }
        val withStart = if (sorted.firstOrNull()?.tick == 0L) {
            sorted
        } else {
            listOf(TempoChange(0, DEFAULT_MICROS_PER_QUARTER)) + sorted
        }
        val built = ArrayList<Segment>(withStart.size)
        for (change in withStart) {
            val previous = built.lastOrNull()
            val startMs = previous?.let {
                it.startMs + ticksToMs(change.tick - it.startTick, it.microsPerQuarter)
            } ?: 0.0
            built += Segment(change.tick, startMs, change.microsPerQuarter)
        }
        segments = built
    }

    fun tickToMs(tick: Long): Double {
        require(tick >= 0) { "tick negativo: $tick" }
        val segment = segments[lastSegmentStartingAtOrBefore(tick)]
        return segment.startMs + ticksToMs(tick - segment.startTick, segment.microsPerQuarter)
    }

    /** Busca binária pelo último trecho com início <= [tick]. O primeiro trecho sempre começa no tick 0. */
    private fun lastSegmentStartingAtOrBefore(tick: Long): Int {
        var low = 0
        var high = segments.lastIndex
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (segments[mid].startTick <= tick) low = mid else high = mid - 1
        }
        return low
    }

    private fun ticksToMs(ticks: Long, microsPerQuarter: Long): Double =
        ticks.toDouble() * microsPerQuarter / resolution / MICROS_PER_MS

    companion object {
        /** 120 BPM, o andamento padrão quando o arquivo não define um no tick 0. */
        const val DEFAULT_MICROS_PER_QUARTER = 500_000L
        private const val MICROS_PER_MS = 1000.0
        private const val MICROS_PER_MINUTE_TIMES_1000 = 60_000_000_000L

        fun microsPerQuarterFromMilliBpm(milliBpm: Long): Long {
            require(milliBpm > 0) { "andamento precisa ser positivo: $milliBpm" }
            return MICROS_PER_MINUTE_TIMES_1000 / milliBpm
        }
    }
}
