package com.rodrigomachado.riff.core.chart

/** Leitor mínimo de Standard MIDI File (formatos 0 e 1), suficiente para mapas do Clone Hero. */
internal object MidiReader {
    data class MidiFile(val ticksPerQuarter: Int, val tracks: List<MidiTrack>)

    data class MidiTrack(val name: String?, val events: List<MidiEvent>)

    sealed interface MidiEvent {
        val tick: Long
    }

    data class NoteOn(override val tick: Long, val key: Int) : MidiEvent

    data class NoteOff(override val tick: Long, val key: Int) : MidiEvent

    data class Tempo(override val tick: Long, val microsPerQuarter: Long) : MidiEvent

    private const val META = 0xFF
    private const val SYSEX = 0xF0
    private const val SYSEX_ESCAPE = 0xF7
    private const val META_TRACK_NAME = 0x03
    private const val META_TEMPO = 0x51
    private const val NOTE_OFF = 0x80
    private const val NOTE_ON = 0x90
    private const val PROGRAM_CHANGE = 0xC0
    private const val CHANNEL_PRESSURE = 0xD0

    fun read(bytes: ByteArray): MidiFile {
        val input = ByteReader(bytes)
        ensureMidi(input.readAscii(4) == "MThd") { "arquivo MIDI inválido: cabeçalho MThd ausente" }
        val headerLength = input.readInt32()
        input.readUInt16() // formato 0 ou 1: tratados igual
        val trackCount = input.readUInt16()
        val division = input.readUInt16()
        ensureMidi(division and 0x8000 == 0) { "MIDI com divisão SMPTE não é suportado" }
        ensureMidi(division != 0) { "MIDI inválido: divisão zero" }
        input.skip(headerLength - 6)
        return MidiFile(division, List(trackCount) { readTrack(input) })
    }

    private fun readTrack(input: ByteReader): MidiTrack {
        ensureMidi(input.readAscii(4) == "MTrk") { "arquivo MIDI inválido: trilha sem MTrk" }
        val length = input.readInt32()
        ensureMidi(length >= 0) { "MIDI inválido: tamanho de trilha negativo" }
        val end = length + input.position
        val state = TrackState()
        while (input.position < end) {
            state.tick += input.readVarLen()
            var status = input.readUInt8()
            if (status < 0x80) {
                if (state.runningStatus == 0) {
                    throw ChartParseException("MIDI inválido: running status sem status anterior")
                }
                input.position -= 1 // o byte lido já era o primeiro dado do evento
                status = state.runningStatus
            }
            when (status) {
                META -> readMeta(input, state)
                SYSEX, SYSEX_ESCAPE -> input.skip(input.readVarLen().toInt())
                else -> readChannelEvent(input, status, state)
            }
        }
        input.position = end
        return MidiTrack(state.name, state.events)
    }

    private class TrackState {
        var tick = 0L
        var runningStatus = 0
        var name: String? = null
        val events = mutableListOf<MidiEvent>()
    }

    private fun readMeta(input: ByteReader, state: TrackState) {
        val type = input.readUInt8()
        val data = input.readBytes(input.readVarLen().toInt())
        if (type == META_TRACK_NAME && state.name == null) state.name = String(data, Charsets.ISO_8859_1)
        if (type == META_TEMPO) state.events += Tempo(state.tick, readTempo(data))
    }

    private fun readChannelEvent(input: ByteReader, status: Int, state: TrackState) {
        state.runningStatus = status
        val type = status and 0xF0
        val key = input.readUInt8()
        val velocity = if (type == PROGRAM_CHANGE || type == CHANNEL_PRESSURE) 0 else input.readUInt8()
        when {
            type == NOTE_ON && velocity > 0 -> state.events += NoteOn(state.tick, key)
            type == NOTE_ON || type == NOTE_OFF -> state.events += NoteOff(state.tick, key)
        }
    }

    private fun readTempo(data: ByteArray): Long {
        ensureMidi(data.size >= 3) { "MIDI inválido: evento de andamento curto" }
        return ((data[0].toLong() and 0xFF) shl 16) or ((data[1].toLong() and 0xFF) shl 8) or
            (data[2].toLong() and 0xFF)
    }
}

internal class ByteReader(private val bytes: ByteArray) {
    var position = 0

    fun readUInt8(): Int {
        ensureMidi(position < bytes.size) { "arquivo MIDI truncado" }
        return bytes[position++].toInt() and 0xFF
    }

    fun readUInt16(): Int = (readUInt8() shl 8) or readUInt8()

    fun readInt32(): Int = (readUInt16() shl 16) or readUInt16()

    fun readVarLen(): Long {
        var value = 0L
        repeat(4) {
            val byte = readUInt8()
            value = (value shl 7) or (byte and 0x7F).toLong()
            if (byte and 0x80 == 0) return value
        }
        throw ChartParseException("MIDI inválido: número de tamanho variável longo demais")
    }

    fun readBytes(count: Int): ByteArray {
        ensureMidi(count >= 0 && position >= 0 && position + count <= bytes.size) { "arquivo MIDI truncado" }
        return bytes.copyOfRange(position, position + count).also { position += count }
    }

    fun readAscii(count: Int): String = String(readBytes(count), Charsets.US_ASCII)

    fun skip(count: Int) {
        readBytes(count)
    }
}

/** Lança [ChartParseException] quando [condition] é falsa. */
internal inline fun ensureMidi(condition: Boolean, message: () -> String) {
    if (!condition) throw ChartParseException(message())
}
