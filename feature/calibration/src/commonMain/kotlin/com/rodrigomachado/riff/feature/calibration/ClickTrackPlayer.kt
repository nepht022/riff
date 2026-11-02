package com.rodrigomachado.riff.feature.calibration

import com.rodrigomachado.riff.core.audio.AudioSink
import com.rodrigomachado.riff.core.audio.AudioSinkFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** Toca o metrônomo e devolve o instante (relógio [calibrationClockMs]) em que cada batida saiu no alto-falante. */
fun interface ClickTrackPlayer {
    suspend fun play(): List<Long>
}

class AudioClickTrackPlayer @Inject constructor(private val sinks: AudioSinkFactory) : ClickTrackPlayer {
    override suspend fun play(): List<Long> = withContext(Dispatchers.IO) {
        val pcm = ClickTrack.render(SAMPLE_RATE)
        val timeline = ClickTimeline(SAMPLE_RATE, ClickTrack.clickFrames(SAMPLE_RATE))
        val sink = sinks.create(SAMPLE_RATE)
        try {
            sink.start()
            coroutineScope {
                var written = false
                var stalled = false
                // Uma saída que abre mas não toca (fone desconectado, driver parado) segura a escrita para sempre: a
                // observação a solta (parando a saída) se a medição for cancelada ou se a posição não andar.
                val watcher = launch {
                    var last = -1L
                    var stalledChecks = 0
                    try {
                        while (isActive) {
                            val played = sink.playedFrames
                            timeline.observe(calibrationClockMs(), played)
                            stalledChecks = if (played == last) stalledChecks + 1 else 0
                            last = played
                            if (stalledChecks >= WRITE_STALLED_CHECKS) {
                                stalled = true
                                sink.stop()
                                return@launch
                            }
                            delay(OBSERVE_EVERY_MS)
                        }
                    } finally {
                        if (!written) sink.stop()
                    }
                }
                var offset = 0
                val total = pcm.size / 2
                while (offset < total && isActive && !stalled) {
                    val count = minOf(CHUNK_FRAMES, total - offset)
                    offset += maxOf(0, sink.write(pcm, offset, count))
                }
                if (stalled) throw IOException("a saída de áudio parou de tocar")
                written = true
                // Um leitor da posição por vez (ela guarda estado entre leituras): a espera do fim assume a observação.
                watcher.cancelAndJoin()
                waitUntilPlayed(sink, total, timeline)
            }
            timeline.clickTimesMs()
        } finally {
            sink.stop()
            sink.release()
        }
    }

    /** Espera o fim, mas não para sempre: o sistema às vezes segura o último pedaço e a saída para de andar. */
    private suspend fun waitUntilPlayed(sink: AudioSink, total: Int, timeline: ClickTimeline) {
        var last = -1L
        var stalledChecks = 0
        while (currentCoroutineContext().isActive) {
            val played = sink.playedFrames
            timeline.observe(calibrationClockMs(), played)
            if (played >= total) return
            stalledChecks = if (played == last) stalledChecks + 1 else 0
            if (stalledChecks >= STALLED_CHECKS) return
            last = played
            delay(OBSERVE_EVERY_MS)
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CHUNK_FRAMES = 1_024
        const val OBSERVE_EVERY_MS = 20L

        /** 1 s parada (50 leituras de 20 ms). */
        const val STALLED_CHECKS = 50

        /** 3 s parada durante a escrita (o PC leva ~650 ms para o primeiro som sair; folga para a máquina lenta). */
        const val WRITE_STALLED_CHECKS = 150
    }
}
