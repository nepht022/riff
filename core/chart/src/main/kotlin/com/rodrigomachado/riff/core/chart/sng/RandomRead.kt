package com.rodrigomachado.riff.core.chart.sng

import java.io.Closeable
import java.io.IOException

/** Leitura em qualquer posição (arquivo do SAF, bytes em memória ou um arquivo de dentro do `.sng`). */
interface RandomRead : Closeable {
    val size: Long

    /** Lê até [length] bytes a partir de [position]; -1 no fim. */
    fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int

    override fun close() = Unit
}

fun interface RandomReadOpener {
    @Throws(IOException::class)
    fun open(uri: String): RandomRead
}

class ByteArrayRandomRead(private val bytes: ByteArray) : RandomRead {
    override val size: Long get() = bytes.size.toLong()

    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= bytes.size) return -1
        val count = minOf(length.toLong(), bytes.size - position).toInt()
        System.arraycopy(bytes, position.toInt(), buffer, offset, count)
        return count
    }
}

/** Lê exatamente [length] bytes ou lança [InvalidSngException] (arquivo truncado). */
internal fun RandomRead.readFully(position: Long, length: Int): ByteArray {
    val out = ByteArray(length)
    var done = 0
    while (done < length) {
        val n = read(position + done, out, done, length - done)
        if (n <= 0) throw InvalidSngException("arquivo truncado")
        done += n
    }
    return out
}
