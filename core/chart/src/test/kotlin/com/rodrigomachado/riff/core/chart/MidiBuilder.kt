package com.rodrigomachado.riff.core.chart

import java.io.ByteArrayOutputStream

/** Monta um Standard MIDI File (formato 1) em memória para os testes. */
class MidiBuilder(private val ticksPerQuarter: Int = 480) {
    private val tracks = mutableListOf<TrackBuilder>()

    fun track(name: String? = null, block: TrackBuilder.() -> Unit = {}): MidiBuilder {
        tracks += TrackBuilder(name).apply(block)
        return this
    }

    fun build(): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeAscii("MThd")
        out.writeInt32(6)
        out.writeInt16(1)
        out.writeInt16(tracks.size)
        out.writeInt16(ticksPerQuarter)
        for (track in tracks) {
            val body = track.encode()
            out.writeAscii("MTrk")
            out.writeInt32(body.size)
            out.write(body)
        }
        return out.toByteArray()
    }

    class TrackBuilder(private val name: String?) {
        private val events = mutableListOf<Pair<Long, ByteArray>>()

        fun tempo(tick: Long, microsPerQuarter: Int) = raw(
            tick,
            0xFF,
            0x51,
            0x03,
            microsPerQuarter shr 16 and 0xFF,
            microsPerQuarter shr 8 and 0xFF,
            microsPerQuarter and 0xFF,
        )

        fun note(tick: Long, key: Int, lengthTicks: Long) {
            raw(tick, 0x90, key, 100)
            raw(tick + lengthTicks, 0x80, key, 0)
        }

        /** Bytes crus após o delta time; sem byte de status, vira running status. */
        fun raw(tick: Long, vararg bytes: Int) {
            events += tick to ByteArray(bytes.size) { bytes[it].toByte() }
        }

        internal fun encode(): ByteArray {
            val out = ByteArrayOutputStream()
            if (name != null) {
                out.writeVarLen(0)
                out.write(0xFF)
                out.write(0x03)
                out.writeVarLen(name.length.toLong())
                out.writeAscii(name)
            }
            var lastTick = 0L
            for ((tick, bytes) in events.sortedBy { it.first }) {
                out.writeVarLen(tick - lastTick)
                out.write(bytes)
                lastTick = tick
            }
            out.writeVarLen(0)
            out.write(0xFF)
            out.write(0x2F)
            out.write(0x00)
            return out.toByteArray()
        }
    }
}

private fun ByteArrayOutputStream.writeAscii(text: String) = write(text.toByteArray(Charsets.US_ASCII))

private fun ByteArrayOutputStream.writeInt16(value: Int) {
    write(value shr 8 and 0xFF)
    write(value and 0xFF)
}

private fun ByteArrayOutputStream.writeInt32(value: Int) {
    writeInt16(value shr 16 and 0xFFFF)
    writeInt16(value and 0xFFFF)
}

private fun ByteArrayOutputStream.writeVarLen(value: Long) {
    var buffer = value and 0x7F
    var rest = value shr 7
    while (rest > 0) {
        buffer = (buffer shl 8) or ((rest and 0x7F) or 0x80)
        rest = rest shr 7
    }
    while (true) {
        write((buffer and 0xFF).toInt())
        if (buffer and 0x80 != 0L) buffer = buffer shr 8 else return
    }
}
