package com.rodrigomachado.riff.core.audio

import android.media.MediaDataSource
import com.rodrigomachado.riff.core.chart.sng.RandomRead
import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
import com.rodrigomachado.riff.core.chart.sng.SngPackage
import com.rodrigomachado.riff.core.chart.sng.SngRef
import java.io.Closeable

/** Um arquivo de dentro do `.sng`, desmascarado, para o `MediaPlayer` e o `MediaExtractor`. */
class SngMediaDataSource(private val file: RandomRead, private val owner: Closeable) : MediaDataSource() {
    override fun getSize(): Long = file.size

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
        if (size == 0) 0 else file.read(position, buffer, offset, size)

    override fun close() = owner.close()
}

/** A fonte de dados do áudio [uri] se ele estiver dentro de um `.sng`; null para um arquivo comum. */
fun audioDataSource(uri: String, opener: RandomReadOpener): MediaDataSource? {
    val (packageFile, name) = SngRef.entryOf(uri) ?: return null
    val sng = SngPackage.open(opener.open(packageFile))
    return SngMediaDataSource(sng.open(name), sng)
}
