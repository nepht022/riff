package com.rodrigomachado.riff.core.audio

import java.nio.ByteBuffer

/** Blocos que o decodificador entrega, um de cada vez. */
internal interface DecodedBlocks {
    val channelCount: Int
    val floatOutput: Boolean

    /** O último bloco já saiu: [next] não vai entregar mais nada. */
    val endOfStream: Boolean

    /** O próximo bloco decodificado, ou null se ainda não há nenhum pronto. */
    fun next(): ByteBuffer?

    /** Devolve ao decodificador o bloco entregue por último. */
    fun release()
}

/**
 * Lê amostras float de [blocks]. Nunca prende a thread de áudio: um decodificador que para de entregar ou que muda
 * de canais no meio da faixa vira [IllegalStateException] (quem lê registra e trata a faixa como silêncio).
 */
internal class PcmBlockReader(private val blocks: DecodedBlocks, private val maxEmptyPolls: Int = MAX_EMPTY_POLLS) {
    private var current: ByteBuffer? = null

    fun prime(attempts: Int) {
        repeat(attempts) {
            if (current != null || blocks.endOfStream) return
            current = blocks.next()
        }
    }

    fun read(buffer: FloatArray, maxFrames: Int): Int {
        val channels = blocks.channelCount
        val wanted = maxFrames * channels
        var filled = 0
        var emptyPolls = 0
        while (filled < wanted && (current != null || !blocks.endOfStream)) {
            val block = current ?: blocks.next()?.also { current = it }
            if (block == null) {
                check(++emptyPolls < maxEmptyPolls) { "o decodificador parou de entregar áudio" }
                continue
            }
            emptyPolls = 0
            check(blocks.channelCount == channels) { "a faixa mudou de $channels para ${blocks.channelCount} canais" }
            filled += copy(block, buffer, filled, wanted - filled)
            // Uma sobra menor que uma amostra não seria lida nunca: o bloco acabou.
            if (block.remaining() < sampleBytes) {
                blocks.release()
                current = null
            }
        }
        return filled / channels
    }

    private val sampleBytes get() = if (blocks.floatOutput) Float.SIZE_BYTES else Short.SIZE_BYTES

    /** Depois de reposicionar o decodificador: o bloco guardado não vale mais. */
    fun reset() {
        current = null
    }

    private fun copy(source: ByteBuffer, target: FloatArray, offset: Int, max: Int): Int {
        var count = 0
        if (blocks.floatOutput) {
            while (count < max && source.remaining() >= Float.SIZE_BYTES) target[offset + count++] = source.float
        } else {
            while (count < max && source.remaining() >= Short.SIZE_BYTES) {
                target[offset + count++] = source.short / SHORT_SCALE
            }
        }
        return count
    }

    companion object {
        private const val SHORT_SCALE = 32_768f

        // Cada tentativa espera até 20 ms no decodificador (entrada e saída): uns 4 s sem nada.
        private const val MAX_EMPTY_POLLS = 200
    }
}
