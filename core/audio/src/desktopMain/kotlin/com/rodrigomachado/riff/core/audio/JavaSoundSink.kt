package com.rodrigomachado.riff.core.audio

import java.io.IOException
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine

/**
 * Saída de som do PC (Java Sound): estéreo 16 bits. Escreve só o que cabe no buffer da linha, em pedaços, para que
 * pausar ou parar interrompa a escrita (a linha parada não desbloquearia uma escrita cheia).
 *
 * @throws IOException se o PC não tem saída de som disponível (sem dispositivo, ou ocupado).
 */
class JavaSoundSink(sampleRate: Int, openLine: (AudioFormat) -> SourceDataLine = { format -> defaultLine(format) }) :
    AudioSink {
    private val format = AudioFormat(sampleRate.toFloat(), BITS, CHANNELS, true, false)
    private val line: SourceDataLine = try {
        openLine(format)
    } catch (e: LineUnavailableException) {
        throw IOException("sem saída de som: ${e.message}", e)
    } catch (e: IllegalArgumentException) {
        throw IOException("sem saída de som: ${e.message}", e)
    }
    private val presented = PresentedFrames(sampleRate)
    private val bytes = ByteArray(CHUNK_FRAMES * FRAME_BYTES)

    @Volatile private var paused = true

    @Volatile private var stopped = false

    override fun start() {
        paused = false
        presented.started(System.nanoTime())
        line.start()
    }

    override fun pause() {
        paused = true
        line.stop()
        presented.paused()
    }

    override fun stop() {
        stopped = true
        paused = true
        line.stop()
        line.flush()
    }

    override fun write(buffer: FloatArray, offsetFrames: Int, frames: Int): Int {
        var done = 0
        while (done < frames && !paused && !stopped) {
            val room = line.available() / FRAME_BYTES
            if (room == 0) {
                Thread.sleep(WAIT_MS)
                continue
            }
            val count = minOf(frames - done, room, CHUNK_FRAMES)
            floatToPcm16(buffer, (offsetFrames + done) * CHANNELS, count * CHANNELS, bytes)
            line.write(bytes, 0, count * FRAME_BYTES)
            done += count
        }
        return done
    }

    override val playedFrames: Long
        get() {
            if (paused) return presented.frames
            // A posição da linha já é a leitura de agora: vale como timestamp (sem ela, o relógio esperaria 1 s).
            val head = line.longFramePosition
            val now = System.nanoTime()
            return presented.update(head = head, stampFrame = head, stampNs = now, nowNs = now)
        }

    override val isPresenting: Boolean get() = presented.isPresenting

    override fun release() = line.close()

    companion object {
        private const val BITS = 16
        private const val CHANNELS = 2
        private const val FRAME_BYTES = BITS / 8 * CHANNELS

        /** O buffer da linha: 2.048 quadros (~43 ms a 48 kHz). */
        const val BUFFER_FRAMES = 2_048
        private const val CHUNK_FRAMES = 512
        private const val WAIT_MS = 2L

        private fun defaultLine(format: AudioFormat): SourceDataLine =
            AudioSystem.getSourceDataLine(format).apply { open(format, BUFFER_FRAMES * FRAME_BYTES) }
    }
}

/** Converte [samples] amostras float (de [offset]) para 16 bits little-endian em [out], cortando em ±1. */
fun floatToPcm16(buffer: FloatArray, offset: Int, samples: Int, out: ByteArray) {
    for (i in 0 until samples) {
        val value = (buffer[offset + i].coerceIn(-1f, 1f) * PCM16_MAX).toInt().coerceIn(PCM16_MIN, PCM16_MAX.toInt())
        out[i * 2] = value.toByte()
        out[i * 2 + 1] = (value shr BYTE_BITS).toByte()
    }
}

private const val PCM16_MAX = 32_767f
private const val PCM16_MIN = -32_768
private const val BYTE_BITS = 8
