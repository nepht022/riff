package com.rodrigomachado.riff.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import javax.sound.sampled.LineUnavailableException

class JavaSoundSinkTest {
    @Test
    fun pcm16ClipsAndConverts() {
        val out = ByteArray(8)

        floatToPcm16(floatArrayOf(1.5f, -1.5f, 0.5f, 0f), 0, 4, out)

        // little-endian, escala simétrica: 32767, -32767, 16383, 0
        assertArrayEquals(byteArrayOf(-1, 127, 1, -128, -1, 63, 0, 0), out)
    }

    @Test
    fun noOutputDeviceIsAnIOException() {
        assertThrows(IOException::class.java) {
            JavaSoundSink(48_000) { throw LineUnavailableException("sem placa de som") }
        }
    }
}
