package com.rodrigomachado.riff.core.chart.sng

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Monta um `.sng` (SngFileFormat, little-endian) para os testes. */
object SngWriter {
    fun write(
        metadata: Map<String, String>,
        files: Map<String, ByteArray>,
        mask: ByteArray = ByteArray(16) { (it * 37 + 11).toByte() },
    ): ByteArray {
        val meta = ByteArrayOutputStream()
        metadata.forEach { (key, value) ->
            val k = key.toByteArray(); val v = value.toByteArray()
            meta.write(int32(k.size)); meta.write(k); meta.write(int32(v.size)); meta.write(v)
        }
        val metaSection = uint64(meta.size().toLong() + 8) + uint64(metadata.size.toLong()) + meta.toByteArray()
        val names = files.keys.map { it.toByteArray() }
        val indexBody = names.sumOf { 1 + it.size + 16 }
        val headerSize = 6 + 4 + 16
        val indexSection = 16 + indexBody
        var offset = (headerSize + metaSection.size + indexSection + 8).toLong()
        val index = ByteArrayOutputStream()
        files.values.forEachIndexed { i, content ->
            index.write(names[i].size); index.write(names[i])
            index.write(uint64(content.size.toLong())); index.write(uint64(offset))
            offset += content.size
        }
        val data = ByteArrayOutputStream()
        files.values.forEach { content -> data.write(masked(content, mask)) }
        return "SNGPKG".toByteArray() + int32(1) + mask + metaSection +
            uint64(index.size().toLong() + 8) + uint64(files.size.toLong()) + index.toByteArray() +
            uint64(data.size().toLong()) + data.toByteArray()
    }

    fun masked(content: ByteArray, mask: ByteArray) =
        ByteArray(content.size) { i -> (content[i].toInt() xor mask[i % 16].toInt() xor (i and 0xFF)).toByte() }

    private fun int32(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
    private fun uint64(v: Long) = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()
}
