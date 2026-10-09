package com.rodrigomachado.riff.feature.calibration

import android.os.SystemClock
import com.rodrigomachado.riff.core.audio.AudioSink
import com.rodrigomachado.riff.core.audio.AudioSinkFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Toca o metrônomo e devolve o instante (relógio `uptimeMillis`) em que cada batida saiu no alto-falante. */
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
                val watcher = launch {
                    while (isActive) {
                        timeline.observe(SystemClock.uptimeMillis(), sink.playedFrames)
                        delay(OBSERVE_EVERY_MS)
                    }
                }
                var offset = 0
                val total = pcm.size / 2
                while (offset < total && isActive) {
                    val count = minOf(CHUNK_FRAMES, total - offset)
                    offset += maxOf(0, sink.write(pcm, offset, count))
                }
                waitUntilPlayed(sink, total)
                watcher.cancel()
            }
            timeline.clickTimesMs()
        } finally {
            sink.stop()
            sink.release()
        }
    }

    /** Espera o fim, mas não para sempre: o sistema às vezes segura o último pedaço e a saída para de andar. */
    private suspend fun waitUntilPlayed(sink: AudioSink, total: Int) {
        var last = -1L
        var stalledChecks = 0
        while (currentCoroutineContext().isActive) {
            val played = sink.playedFrames
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
    }
}
