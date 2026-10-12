package com.rodrigomachado.riff.core.data.folder

import android.content.Context
import android.net.Uri
import com.rodrigomachado.riff.core.chart.sng.RandomRead
import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject

/** Arquivo do SAF lido por posição (o `.sng` é lido aos pedaços, nunca inteiro na memória). */
class ContentRandomReadOpener @Inject constructor(@ApplicationContext private val context: Context) :
    RandomReadOpener {
    override fun open(uri: String): RandomRead {
        val descriptor = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
            ?: throw IOException("não foi possível abrir $uri")
        val stream = FileInputStream(descriptor.fileDescriptor)
        val channel = stream.channel
        return object : RandomRead {
            override val size: Long = channel.size()

            override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int =
                channel.read(ByteBuffer.wrap(buffer, offset, length), position)

            override fun close() {
                stream.close()
                descriptor.close()
            }
        }
    }
}
