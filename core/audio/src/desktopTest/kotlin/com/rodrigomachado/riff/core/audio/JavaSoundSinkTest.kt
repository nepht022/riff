package com.rodrigomachado.riff.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine

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

    /** Assim que a linha anda, o som está saindo: sem esperar o segundo de segurança das saídas sem timestamp. */
    @Test
    fun itPresentsAsSoonAsTheLineMoves() {
        var position = 0L
        val line = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SourceDataLine::class.java)) { _, method, _ ->
            when (method.name) {
                "getLongFramePosition" -> position
                "available" -> 0
                "isOpen", "isRunning", "isActive" -> true
                else -> null
            }
        } as SourceDataLine
        val sink = JavaSoundSink(48_000) { line }

        sink.start()
        Thread.sleep(20) // a posição nunca passa do tempo desde que começou
        position = 480 // 10 ms tocados
        sink.playedFrames

        assertTrue(sink.isPresenting)
        assertEquals(480L, sink.playedFrames)
    }
}
