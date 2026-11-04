package com.rodrigomachado.riff.core.separation

import com.rodrigomachado.riff.core.data.SEPARATED_BACKING
import com.rodrigomachado.riff.core.data.SEPARATED_GUITAR
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avformat.AVIOContext
import org.bytedeco.ffmpeg.avformat.AVStream
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.avutil.AVRational
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avformat
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.FloatPointer
import java.io.File
import java.io.IOException

/**
 * Grava a guitarra e o resto em `.m4a` (AAC-LC 160 kb/s, 44,1 kHz estéreo, como o `M4aStemSink` do celular) na
 * pasta da separação, pelo codificador AAC do próprio FFmpeg.
 */
class FfmpegStemSink(dir: File) : ClosableStemSink {
    private val guitar = FfmpegAacFile(File(dir, SEPARATED_GUITAR))
    private val backing = try {
        FfmpegAacFile(File(dir, SEPARATED_BACKING))
    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
        guitar.release()
        throw e
    }

    override fun write(guitar: FloatArray, backing: FloatArray, frames: Int) {
        this.guitar.write(guitar, frames)
        this.backing.write(backing, frames)
    }

    override fun finish() {
        try {
            guitar.finish()
            backing.finish()
        } finally {
            abort() // o que já fechou não muda; o que falhou no meio é liberado
        }
    }

    override fun abort() {
        guitar.release()
        backing.release()
    }
}

/** Um `.m4a` com uma faixa AAC: recebe áudio intercalado em float e codifica em quadros de 1024 amostras. */
@Suppress("TooManyFunctions") // o ciclo de vida do FFmpeg (abrir, codificar, esvaziar, fechar) fica junto
internal class FfmpegAacFile(private val file: File) {
    private var format: AVFormatContext? = null
    private var codec: AVCodecContext? = null
    private var stream: AVStream? = null
    private var io: AVIOContext? = null
    private val packet: AVPacket = avcodec.av_packet_alloc()
    private val frame: AVFrame = avutil.av_frame_alloc()
    private var frameSize = 0
    private var left = FloatArray(0)
    private var right = FloatArray(0)
    private var buffered = 0
    private var pts = 0L
    private var released = false

    init {
        try {
            open()
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            release()
            throw e
        }
    }

    private fun open() {
        val encoder = found(avcodec.avcodec_find_encoder(avcodec.AV_CODEC_ID_AAC), "o codificador AAC")
        val output = AVFormatContext(null)
        check(avformat.avformat_alloc_output_context2(output, null, "mp4", file.path), "preparar")
        format = output
        val context = found(avcodec.avcodec_alloc_context3(encoder), "memória para o AAC")
        codec = context
        context.sample_fmt(avutil.AV_SAMPLE_FMT_FLTP)
        context.sample_rate(Demucs.SAMPLE_RATE)
        avutil.av_channel_layout_default(context.ch_layout(), 2)
        context.bit_rate(BIT_RATE)
        context.profile(avcodec.AV_PROFILE_AAC_LOW)
        context.time_base(AVRational().num(1).den(Demucs.SAMPLE_RATE))
        if (output.oformat().flags() and avformat.AVFMT_GLOBALHEADER != 0) {
            context.flags(context.flags() or avcodec.AV_CODEC_FLAG_GLOBAL_HEADER)
        }
        check(avcodec.avcodec_open2(context, encoder, null as AVDictionary?), "abrir o codificador de")
        frameSize = context.frame_size()
        left = FloatArray(frameSize)
        right = FloatArray(frameSize)
        val track = found(avformat.avformat_new_stream(output, null), "memória para a faixa")
        stream = track
        check(avcodec.avcodec_parameters_from_context(track.codecpar(), context), "descrever")
        track.time_base(context.time_base())
        val pb = AVIOContext(null)
        check(avformat.avio_open(pb, file.path, avformat.AVIO_FLAG_WRITE), "criar")
        io = pb
        output.pb(pb)
        check(avformat.avformat_write_header(output, null as AVDictionary?), "começar")
    }

    fun write(interleaved: FloatArray, frames: Int) {
        var done = 0
        while (done < frames) {
            val count = minOf(frames - done, frameSize - buffered)
            for (k in 0 until count) {
                left[buffered + k] = interleaved[2 * (done + k)].coerceIn(-1f, 1f)
                right[buffered + k] = interleaved[2 * (done + k) + 1].coerceIn(-1f, 1f)
            }
            buffered += count
            done += count
            if (buffered == frameSize) encodeBuffered()
        }
    }

    /** Codifica o resto (o último quadro pode ser menor), esvazia o codificador e fecha o arquivo. */
    fun finish() {
        if (buffered > 0) encodeBuffered()
        send(null)
        val output = checkNotNull(format)
        check(avformat.av_write_trailer(output), "terminar")
        check(avformat.avio_closep(output.pb()), "fechar")
        io = null
    }

    /** Libera tudo; sem o [finish], o arquivo fica inválido (é apagado com a pasta temporária). */
    fun release() {
        if (released) return
        released = true
        codec?.let { avcodec.avcodec_free_context(it) }
        codec = null
        format?.let { output ->
            if (io != null) avformat.avio_closep(output.pb())
            io = null
            avformat.avformat_free_context(output)
        }
        format = null
        avcodec.av_packet_free(packet)
        avutil.av_frame_free(frame)
    }

    private fun encodeBuffered() {
        val context = checkNotNull(codec)
        frame.nb_samples(buffered)
        frame.format(context.sample_fmt())
        frame.sample_rate(context.sample_rate())
        check(avutil.av_channel_layout_copy(frame.ch_layout(), context.ch_layout()), "preparar o quadro de")
        check(avutil.av_frame_get_buffer(frame, 0), "preparar o quadro de")
        FloatPointer(frame.data(0)).put(left, 0, buffered)
        FloatPointer(frame.data(1)).put(right, 0, buffered)
        frame.pts(pts)
        pts += buffered
        buffered = 0
        try {
            send(frame)
        } finally {
            avutil.av_frame_unref(frame)
        }
    }

    /** Manda [input] ao codificador (nulo = fim) e grava os pacotes prontos. */
    private fun send(input: AVFrame?) {
        val context = checkNotNull(codec)
        check(avcodec.avcodec_send_frame(context, input), "codificar")
        val track = checkNotNull(stream)
        while (true) {
            val received = avcodec.avcodec_receive_packet(context, packet)
            if (received == avutil.AVERROR_EAGAIN() || received == avutil.AVERROR_EOF()) return
            check(received, "codificar")
            avcodec.av_packet_rescale_ts(packet, context.time_base(), track.time_base())
            packet.stream_index(track.index())
            check(avformat.av_interleaved_write_frame(checkNotNull(format), packet), "gravar")
        }
    }

    private fun <T : Any> found(value: T?, what: String): T =
        value ?: throw IOException("o FFmpeg do Riff ficou sem $what ao gravar ${file.path}")

    private fun check(status: Int, what: String) {
        if (status < 0) throw IOException("não deu para $what ${file.path}: ${errorText(status)}")
    }

    private companion object {
        const val BIT_RATE = 160_000L
        const val ERROR_TEXT_BYTES = 256L

        fun errorText(status: Int): String {
            val text = BytePointer(ERROR_TEXT_BYTES)
            avutil.av_strerror(status, text, ERROR_TEXT_BYTES)
            return text.string.substringBefore('\u0000')
        }
    }
}
