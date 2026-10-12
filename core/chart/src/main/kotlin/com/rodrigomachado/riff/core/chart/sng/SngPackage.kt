package com.rodrigomachado.riff.core.chart.sng

import com.rodrigomachado.riff.core.chart.SongInfo
import com.rodrigomachado.riff.core.chart.SongIniParser
import java.io.Closeable
import java.io.IOException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class InvalidSngException(message: String) : IOException(message)

data class SngFile(val name: String, val size: Long, val offset: Long)

/**
 * Pacote `.sng` (SngFileFormat): cabeçalho "SNGPKG", metadados (as chaves do song.ini), índice e os arquivos
 * mascarados. Cada byte `i` de um arquivo é `byte XOR máscara[i % 16] XOR (i AND 0xFF)`.
 */
class SngPackage private constructor(
    private val source: RandomRead,
    private val mask: ByteArray,
    val metadata: Map<String, String>,
    val files: List<SngFile>,
) : Closeable {

    fun read(name: String, maxBytes: Long): ByteArray {
        val file = file(name)
        if (file.size > maxBytes) throw IOException("$name grande demais (${file.size} bytes)")
        val bytes = source.readFully(file.offset, file.size.toInt())
        unmask(bytes, 0L)
        return bytes
    }

    fun open(name: String): RandomRead {
        val file = file(name)
        return object : RandomRead {
            override val size: Long get() = file.size

            override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
                if (position >= file.size) return -1
                val count = minOf(length.toLong(), file.size - position).toInt()
                val n = source.read(file.offset + position, buffer, offset, count)
                if (n > 0) unmask(buffer, position, offset, n)
                return n
            }
        }
    }

    /** Os metadados no formato do song.ini, pelas mesmas regras do [SongIniParser]. */
    fun songInfo(): SongInfo =
        SongIniParser.parse("[song]\n" + metadata.entries.joinToString("\n") { "${it.key} = ${it.value}" })

    private fun file(name: String) = files.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: throw IOException("$name não está no pacote")

    private fun unmask(bytes: ByteArray, start: Long, offset: Int = 0, length: Int = bytes.size) {
        for (k in 0 until length) {
            val i = start + k
            val key = mask[(i % MASK_SIZE).toInt()].toInt() xor (i and 0xFF).toInt()
            bytes[offset + k] = (bytes[offset + k].toInt() xor key).toByte()
        }
    }

    override fun close() = source.close()

    companion object {
        internal const val MASK_SIZE = 16

        /** @throws InvalidSngException cabeçalho errado, arquivo truncado ou tamanhos fora do arquivo. */
        fun open(source: RandomRead): SngPackage {
            val headSize = minOf(source.size, SngIndex.MAX_INDEX_BYTES.toLong()).toInt()
            val head = source.readFully(0, headSize)
            val index = when (val parsed = SngIndex.parse(head)) {
                is SngIndex.Result.Complete -> parsed
                is SngIndex.Result.NeedsMore -> throw InvalidSngException("índice maior que o arquivo")
            }
            index.files.forEach { if (it.offset + it.size > source.size) throw InvalidSngException("${it.name} fora") }
            return SngPackage(source, head.copyOfRange(10, 26), index.metadata, index.files)
        }
    }
}

/** Lê cabeçalho, metadados e índice a partir só do começo do arquivo (o resto são os dados). */
object SngIndex {
    const val MAX_INDEX_BYTES = 256 * 1024
    private const val MAX_FILES = 64
    private const val MAX_METADATA = 4_096
    private const val HEADER_BYTES = 26

    sealed interface Result {
        data class Complete(val metadata: Map<String, String>, val files: List<SngFile>) : Result
        data class NeedsMore(val bytes: Int) : Result
    }

    @Suppress("SwallowedException") // faltar bytes no começo do arquivo não é erro: pede mais
    fun parse(head: ByteArray): Result {
        if (head.size < HEADER_BYTES) return Result.NeedsMore(HEADER_BYTES)
        if (String(head, 0, 6) != "SNGPKG") invalid("não é um .sng")
        val buf = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        return try {
            buf.position(HEADER_BYTES)
            val metadata = metadata(buf)
            buf.long // tamanho do índice
            Result.Complete(metadata, files(buf))
        } catch (e: BufferUnderflowException) {
            if (head.size >= MAX_INDEX_BYTES) invalid("índice grande demais")
            Result.NeedsMore(minOf(head.size * 2, MAX_INDEX_BYTES))
        }
    }

    private fun metadata(buf: ByteBuffer): Map<String, String> {
        val length = buf.long
        val count = buf.long
        if (count !in 0..MAX_METADATA || length !in 8..MAX_INDEX_BYTES.toLong()) invalid("metadados inválidos")
        val metadata = LinkedHashMap<String, String>()
        repeat(count.toInt()) { metadata[string(buf, buf.int)] = string(buf, buf.int) }
        return metadata
    }

    private fun files(buf: ByteBuffer): List<SngFile> {
        val count = buf.long
        if (count !in 0..MAX_FILES) invalid("arquivos demais: $count")
        return List(count.toInt()) {
            val name = string(buf, buf.get().toInt() and 0xFF)
            val size = buf.long
            val offset = buf.long
            if (size < 0 || offset < 0 || size > Int.MAX_VALUE) invalid("$name inválido")
            SngFile(name, size, offset)
        }
    }

    private fun invalid(message: String): Nothing = throw InvalidSngException(message)

    private fun string(buf: ByteBuffer, length: Int): String {
        if (length < 0 || length > buf.remaining()) throw BufferUnderflowException()
        val bytes = ByteArray(length).also { buf.get(it) }
        return String(bytes, Charsets.UTF_8)
    }
}
