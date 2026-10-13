package com.rodrigomachado.riff.core.separation

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.rodrigomachado.riff.core.data.SEPARATED_BACKING
import com.rodrigomachado.riff.core.data.SEPARATED_GUITAR
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

interface ClosableStemSink : StemSink {
    /** Fecha os arquivos; sem isto eles ficam inválidos. */
    fun finish()
}

fun interface StemSinkFactory {
    fun create(dir: File): ClosableStemSink
}

/** Grava a guitarra e o resto em `.m4a` (AAC 160 kb/s, 44,1 kHz estéreo) na pasta da separação. */
class M4aStemSink(dir: File) : ClosableStemSink {
    private val guitar = AacFile(File(dir, SEPARATED_GUITAR))
    private val backing = AacFile(File(dir, SEPARATED_BACKING))

    override fun write(guitar: FloatArray, backing: FloatArray, frames: Int) {
        this.guitar.write(guitar, frames)
        this.backing.write(backing, frames)
    }

    override fun finish() {
        guitar.finish()
        backing.finish()
    }
}

private class AacFile(file: File) {
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var framesWritten = 0L

    init {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, Demucs.SAMPLE_RATE, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    fun write(interleaved: FloatArray, frames: Int) {
        var done = 0
        while (done < frames) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index < 0) {
                drain(false)
                continue
            }
            val input = checkNotNull(codec.getInputBuffer(index)).order(ByteOrder.LITTLE_ENDIAN)
            val count = minOf(frames - done, input.remaining() / BYTES_PER_FRAME)
            for (i in 2 * done until 2 * (done + count)) input.putShort(toPcm16(interleaved[i]))
            codec.queueInputBuffer(index, 0, count * BYTES_PER_FRAME, presentationUs(), 0)
            framesWritten += count
            done += count
            drain(false)
        }
    }

    fun finish() {
        var index = codec.dequeueInputBuffer(TIMEOUT_US)
        while (index < 0) {
            drain(false)
            index = codec.dequeueInputBuffer(TIMEOUT_US)
        }
        codec.queueInputBuffer(index, 0, 0, presentationUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        drain(true)
        codec.stop()
        codec.release()
        muxer.stop()
        muxer.release()
    }

    private fun drain(untilEnd: Boolean) {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                }

                index >= 0 -> {
                    val data: ByteBuffer = checkNotNull(codec.getOutputBuffer(index))
                    val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!config && info.size > 0) muxer.writeSampleData(track, data, info)
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }

                !untilEnd -> return
            }
        }
    }

    private fun presentationUs() = framesWritten * 1_000_000L / Demucs.SAMPLE_RATE

    private companion object {
        const val BIT_RATE = 160_000
        const val MAX_INPUT = 64 * 1024
        const val BYTES_PER_FRAME = 4
        const val TIMEOUT_US = 10_000L

        fun toPcm16(x: Float): Short = (x.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
    }
}
