package com.rodrigomachado.riff.core.audio

import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
import com.rodrigomachado.riff.core.chart.sng.SngPackage
import com.rodrigomachado.riff.core.chart.sng.SngRef
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avutil.AVChannelLayout
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.avutil.AVRational
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avformat
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.ffmpeg.global.swresample
import org.bytedeco.ffmpeg.swresample.SwrContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.FloatPointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacpp.PointerPointer
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** O formato em que o PC toca tudo: o mixer só aceita faixas de mesma taxa. */
object DesktopAudioFormat {
    const val SAMPLE_RATE = 48_000
    const val CHANNELS = 2
}

/**
 * Uma faixa decodificada pelo FFmpeg, sempre em 48 kHz estéreo float (qualquer formato, taxa ou número de canais
 * na entrada). A faixa de dentro de um `.sng` vai, desmascarada, para um arquivo temporário apagado no [close].
 */
@Suppress("TooManyFunctions") // o ciclo de vida do FFmpeg (abrir, ler, reposicionar, fechar) fica junto
class FfmpegPcmSource private constructor(private val path: String, private val tempFile: File?) : PcmSource {
    private val format = AVFormatContext(null)
    private var codec: AVCodecContext? = null
    private var resampler: SwrContext? = null
    private val packet: AVPacket = avcodec.av_packet_alloc()
    private val frame: AVFrame = avutil.av_frame_alloc()
    private var stream = -1
    private var outCapacity = 0
    private var outBuffer: BytePointer? = null

    // Quadros já convertidos que a próxima leitura entrega primeiro.
    private var pending = FloatArray(0)
    private var pendingStart = 0
    private var pendingEnd = 0
    private var drained = false
    private var badPackets = 0
    private var inChannels = 0
    private var inRate = 0
    private var inFormat = 0
    private val outputTimeBase = AVRational().num(1).den(DesktopAudioFormat.SAMPLE_RATE)

    // Quadros a descartar depois de um reposicionamento (o FFmpeg volta até o quadro-chave anterior).
    private var discardUntil = -1L
    private var nextFrame = 0L

    override val sampleRate: Int = DesktopAudioFormat.SAMPLE_RATE
    override val channelCount: Int = DesktopAudioFormat.CHANNELS
    override var durationMs: Long? = null
        private set

    private fun openDecoder() {
        check(avformat.avformat_open_input(format, path, null, null as AVDictionary?), "abrir")
        check(avformat.avformat_find_stream_info(format, null as PointerPointer<*>?), "ler o formato")
        stream = avformat.av_find_best_stream(format, avutil.AVMEDIA_TYPE_AUDIO, -1, -1, null as PointerPointer<*>?, 0)
        if (stream < 0) throw IOException("sem áudio em $path")
        val parameters = format.streams(stream).codecpar()
        val decoder = avcodec.avcodec_find_decoder(parameters.codec_id())
            ?: throw IOException("formato de áudio desconhecido em $path")
        val context = avcodec.avcodec_alloc_context3(decoder).also { codec = it }
        check(avcodec.avcodec_parameters_to_context(context, parameters), "ler o áudio")
        check(avcodec.avcodec_open2(context, decoder, null as AVDictionary?), "abrir o decodificador")
        resampler = openResampler(context.ch_layout(), context.sample_rate(), context.sample_fmt())
        format.duration().takeIf { it > 0 }?.let { durationMs = it / MICROS_PER_MS }
    }

    /**
     * O conversor para 48 kHz estéreo float, a partir do formato de entrada. Um quadro com outro formato (fluxo que
     * muda de canais ou de taxa no meio) refaz o conversor: passar ao conversor um quadro de outro formato lê fora
     * do buffer.
     */
    private fun openResampler(layout: AVChannelLayout, rate: Int, sampleFormat: Int): SwrContext {
        resampler?.let { swresample.swr_free(it) }
        resampler = null
        if (layout.order() == avutil.AV_CHANNEL_ORDER_UNSPEC) {
            avutil.av_channel_layout_default(layout, layout.nb_channels())
        }
        inChannels = layout.nb_channels()
        inRate = rate
        inFormat = sampleFormat
        val output = AVChannelLayout().also {
            check(avutil.av_channel_layout_from_mask(it, avutil.AV_CH_LAYOUT_STEREO), "preparar a conversão")
        }
        // Guardado já: se uma opção falhar, o close() libera o conversor.
        val swr = swresample.swr_alloc() ?: throw IOException("sem memória para converter o áudio")
        resampler = swr
        check(avutil.av_opt_set_chlayout(swr, "in_chlayout", layout, 0), "ler os canais")
        check(avutil.av_opt_set_int(swr, "in_sample_rate", rate.toLong(), 0), "ler a taxa")
        check(avutil.av_opt_set_sample_fmt(swr, "in_sample_fmt", sampleFormat, 0), "ler as amostras")
        check(avutil.av_opt_set_chlayout(swr, "out_chlayout", output, 0), "preparar os canais")
        check(avutil.av_opt_set_int(swr, "out_sample_rate", sampleRate.toLong(), 0), "preparar a taxa")
        check(avutil.av_opt_set_sample_fmt(swr, "out_sample_fmt", avutil.AV_SAMPLE_FMT_FLT, 0), "preparar as amostras")
        check(swresample.swr_init(swr), "preparar a conversão")
        return swr
    }

    override fun read(buffer: FloatArray, maxFrames: Int): Int {
        var done = 0
        while (done < maxFrames) {
            if (pendingStart == pendingEnd && !decodeMore()) break
            val frames = minOf(maxFrames - done, (pendingEnd - pendingStart) / channelCount)
            System.arraycopy(pending, pendingStart, buffer, done * channelCount, frames * channelCount)
            pendingStart += frames * channelCount
            done += frames
        }
        return done
    }

    override fun seekTo(frame: Long) {
        val context = codec ?: throw IOException("faixa fechada")
        val target = avutil.av_rescale_q(frame, outputTimeBase, format.streams(stream).time_base()) + origin()
        check(avformat.av_seek_frame(format, stream, target, avformat.AVSEEK_FLAG_BACKWARD), "reposicionar")
        avcodec.avcodec_flush_buffers(context)
        swresample.swr_init(resampler)
        pendingStart = 0
        pendingEnd = 0
        drained = false
        discardUntil = frame
        nextFrame = -1L
    }

    /** Decodifica até ter quadros novos convertidos; falso no fim da faixa. */
    private fun decodeMore(): Boolean {
        val context = codec ?: return false
        while (pendingStart == pendingEnd) {
            if (drained) return false
            val received = avcodec.avcodec_receive_frame(context, frame)
            when {
                received == 0 -> {
                    badPackets = 0
                    convert(frame)
                }

                received == avutil.AVERROR_EOF() -> {
                    flushResampler()
                    drained = true
                }

                received == avutil.AVERROR_EAGAIN() -> feedDecoder(context)

                else -> skipBadData()
            }
        }
        return true
    }

    private fun feedDecoder(context: AVCodecContext) {
        while (true) {
            val status = avformat.av_read_frame(format, packet)
            if (status < 0) {
                avcodec.avcodec_send_packet(context, null as AVPacket?) // fim: o decodificador entrega o resto
                return
            }
            val mine = packet.stream_index() == stream
            if (mine) {
                val sent = avcodec.avcodec_send_packet(context, packet)
                avcodec.av_packet_unref(packet)
                if (sent >= 0 || sent == avutil.AVERROR_EAGAIN()) return
                skipBadData() // pacote corrompido: o próximo segue (como o ffmpeg faz)
                if (drained) return
            }
            avcodec.av_packet_unref(packet)
        }
    }

    /**
     * Um trecho corrompido no meio da música (download incompleto, quadro ruim) é pulado; só depois de muitos
     * seguidos a faixa acaba ali, em vez de derrubar a música.
     */
    private fun skipBadData() {
        if (++badPackets <= MAX_BAD_PACKETS) return
        flushResampler()
        drained = true
    }

    /**
     * Onde a posição 0 da música cai, nas unidades do fluxo: o início positivo do fluxo (no mp3, o atraso do
     * codificador), menos as amostras iniciais que o decodificador descarta (o "pre-skip" do Opus): nos tempos
     * dos pacotes do Opus elas ficam antes do zero.
     */
    private fun origin(): Long {
        val media = format.streams(stream)
        val start = media.start_time().takeIf { it != avutil.AV_NOPTS_VALUE }?.coerceAtLeast(0) ?: 0
        val parameters = media.codecpar()
        val padding = parameters.initial_padding().toLong().takeIf { it > 0 && parameters.sample_rate() > 0 }
            ?.let { avutil.av_rescale_q(it, AVRational().num(1).den(parameters.sample_rate()), media.time_base()) }
        return start - (padding ?: 0)
    }

    private fun convert(decoded: AVFrame) {
        if (nextFrame < 0) {
            val pts = decoded.best_effort_timestamp()
            nextFrame = if (pts == avutil.AV_NOPTS_VALUE) {
                discardUntil.coerceAtLeast(0)
            } else {
                avutil.av_rescale_q(pts - origin(), format.streams(stream).time_base(), outputTimeBase)
            }
        }
        if (inputChanged(decoded)) {
            resampler = openResampler(decoded.ch_layout(), decoded.sample_rate(), decoded.format())
        }
        val capacity = swresample.swr_get_out_samples(resampler, decoded.nb_samples())
        val converted = swresample.swr_convert(
            resampler,
            pointerTo(outBytes(capacity)),
            capacity,
            decoded.extended_data(),
            decoded.nb_samples(),
        )
        avutil.av_frame_unref(decoded)
        if (converted < 0) throw IOException("não deu para converter o áudio de $path")
        deliver(converted)
    }

    private fun inputChanged(decoded: AVFrame): Boolean =
        decoded.ch_layout().nb_channels() != inChannels || decoded.sample_rate() != inRate ||
            decoded.format() != inFormat

    private fun flushResampler() {
        val capacity = swresample.swr_get_out_samples(resampler, 0)
        if (capacity <= 0) return
        val converted = swresample.swr_convert(resampler, pointerTo(outBytes(capacity)), capacity, null, 0)
        if (converted > 0) deliver(converted)
    }

    /** Põe [frames] quadros convertidos na fila, sem os que ficam antes do ponto pedido num reposicionamento. */
    private fun deliver(frames: Int) {
        var skip = 0
        if (discardUntil >= 0) {
            skip = (discardUntil - nextFrame).coerceIn(0, frames.toLong()).toInt()
            if (nextFrame + frames >= discardUntil) discardUntil = -1
        }
        nextFrame += frames
        val samples = (frames - skip) * channelCount
        if (samples <= 0) return
        if (pending.size < samples) pending = FloatArray(samples)
        FloatPointer(outBuffer).position(skip.toLong() * channelCount).get(pending, 0, samples)
        pendingStart = 0
        pendingEnd = samples
    }

    private fun outBytes(frames: Int): BytePointer {
        if (frames > outCapacity) {
            outBuffer?.let { avutil.av_free(it) }
            outBuffer = BytePointer(avutil.av_malloc(frames.toLong() * channelCount * Float.SIZE_BYTES))
            outCapacity = frames
        }
        return outBuffer!!
    }

    override fun close() {
        resampler?.let { swresample.swr_free(it) }
        resampler = null
        codec?.let { avcodec.avcodec_free_context(it) }
        codec = null
        avformat.avformat_close_input(format)
        avcodec.av_packet_free(packet)
        avutil.av_frame_free(frame)
        outBuffer?.let { avutil.av_free(it) }
        outBuffer = null
        tempFile?.delete()
    }

    private fun check(status: Int, what: String) {
        if (status < 0) throw IOException("não deu para $what $path: ${errorText(status)}")
    }

    companion object {
        private const val MICROS_PER_MS = 1_000L
        private const val FILE_URI = "file://"
        private const val MAX_BAD_PACKETS = 50
        private const val ERROR_TEXT_BYTES = 256L

        init {
            avutil.av_log_set_level(avutil.AV_LOG_ERROR)
        }

        /**
         * Abre a faixa [uri]: um caminho do PC, um `file://` + caminho (a guitarra separada por IA, no formato comum
         * com o celular) ou uma entrada de `.sng` ([SngRef]), lida pelo [randomRead].
         *
         * @throws IOException se o arquivo não existe, não é áudio ou o formato não é conhecido.
         */
        fun open(uri: String, randomRead: RandomReadOpener): FfmpegPcmSource {
            val entry = SngRef.entryOf(uri)
            val temp = entry?.let { (packageFile, name) -> extract(packageFile, name, randomRead) }
            // O FFmpeg tiraria só o "file:" (no Windows ficaria "//C:/...").
            val source = FfmpegPcmSource(temp?.path ?: uri.removePrefix(FILE_URI), temp)
            try {
                source.openDecoder()
            } catch (e: IOException) {
                source.close()
                throw e
            }
            return source
        }

        /** A faixa [name] de dentro do pacote, desmascarada num arquivo temporário (`riff-*`). */
        private fun extract(packageFile: String, name: String, randomRead: RandomReadOpener): File {
            val bytes = SngPackage.open(randomRead.open(packageFile)).use { it.read(name, Long.MAX_VALUE) }
            val file = Files.createTempFile("riff-", "." + name.substringAfterLast('.', "bin")).toFile()
            file.writeBytes(bytes)
            return file
        }

        /**
         * Uma lista de um ponteiro, [pointer]. Não use `PointerPointer(pointer)`: com um argumento só, o Kotlin escolhe
         * o construtor que reinterpreta o endereço como lista, e o FFmpeg escreve fora do buffer.
         */
        private fun pointerTo(pointer: Pointer): PointerPointer<Pointer> = PointerPointer<Pointer>(1L).put(0, pointer)

        private fun errorText(status: Int): String {
            val text = BytePointer(ERROR_TEXT_BYTES)
            avutil.av_strerror(status, text, ERROR_TEXT_BYTES)
            return text.string.substringBefore('\u0000')
        }
    }
}
