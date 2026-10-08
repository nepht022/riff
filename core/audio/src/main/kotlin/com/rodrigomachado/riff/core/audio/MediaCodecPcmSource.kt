package com.rodrigomachado.riff.core.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decodifica uma faixa aos poucos (MediaExtractor + MediaCodec), sem carregar a música inteira. */
class MediaCodecPcmSource private constructor(private val extractor: MediaExtractor, private val codec: MediaCodec) :
    PcmSource {
    private val info = MediaCodec.BufferInfo()
    private var output: ByteBuffer? = null
    private var outputIndex = -1
    private var floatOutput = false
    private var inputDone = false
    private var outputDone = false

    override var sampleRate = 0
        private set

    override var channelCount = 0
        private set

    /** Lê até o primeiro bloco decodificado, para saber a taxa e os canais reais (o Opus decodifica a 48 kHz). */
    private fun prime() {
        repeat(PRIME_ATTEMPTS) {
            if (output != null || outputDone) return
            nextOutput()
        }
        if (sampleRate == 0) throw IOException("decodificador não entregou áudio")
    }

    override fun read(buffer: FloatArray, maxFrames: Int): Int {
        val wanted = maxFrames * channelCount
        var filled = 0
        while (filled < wanted && (output != null || !outputDone)) {
            val current = output ?: nextOutput()
            if (current != null) {
                filled += copy(current, buffer, filled, wanted - filled)
                if (!current.hasRemaining()) {
                    codec.releaseOutputBuffer(outputIndex, false)
                    output = null
                    outputIndex = -1
                }
            }
        }
        return filled / channelCount
    }

    private fun copy(source: ByteBuffer, target: FloatArray, offset: Int, max: Int): Int {
        var count = 0
        if (floatOutput) {
            while (count < max && source.remaining() >= Float.SIZE_BYTES) target[offset + count++] = source.float
        } else {
            while (count < max && source.remaining() >= Short.SIZE_BYTES) {
                target[offset + count++] = source.short / SHORT_SCALE
            }
        }
        return count
    }

    private fun nextOutput(): ByteBuffer? {
        feedInput()
        val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) readFormat(codec.outputFormat)
        if (index < 0) return null
        if (sampleRate == 0) readFormat(codec.outputFormat)
        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
        val buffer = checkNotNull(codec.getOutputBuffer(index))
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        buffer.order(ByteOrder.nativeOrder())
        output = buffer
        outputIndex = index
        return buffer
    }

    private fun readFormat(format: MediaFormat) {
        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        floatOutput = format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
            format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
    }

    private fun feedInput() {
        if (inputDone) return
        val index = codec.dequeueInputBuffer(TIMEOUT_US)
        if (index < 0) return
        val buffer = checkNotNull(codec.getInputBuffer(index))
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) {
            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputDone = true
        } else {
            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
            extractor.advance()
        }
    }

    override fun close() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { extractor.release() }
    }

    companion object {
        private const val TIMEOUT_US = 10_000L
        private const val PRIME_ATTEMPTS = 200
        private const val SHORT_SCALE = 32_768f

        @Throws(IOException::class)
        @Suppress("TooGenericExceptionCaught") // erros do MediaCodec chegam como RuntimeException
        fun open(context: Context, uri: String): MediaCodecPcmSource {
            val extractor = MediaExtractor()
            return try {
                startDecoding(context, uri, extractor)
            } catch (e: IOException) {
                extractor.release()
                throw e
            } catch (e: RuntimeException) {
                extractor.release()
                throw IOException("não deu para decodificar $uri", e)
            }
        }

        private fun startDecoding(context: Context, uri: String, extractor: MediaExtractor): MediaCodecPcmSource {
            extractor.setDataSource(context, Uri.parse(uri), null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("sem faixa de áudio: $uri")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec.configure(format, null, null, 0)
            codec.start()
            val source = MediaCodecPcmSource(extractor, codec)
            try {
                source.prime()
            } catch (e: IOException) {
                runCatching { codec.stop() }
                runCatching { codec.release() }
                throw e
            }
            return source
        }
    }
}
