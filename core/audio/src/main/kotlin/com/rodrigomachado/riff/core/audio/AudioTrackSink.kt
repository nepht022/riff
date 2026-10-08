package com.rodrigomachado.riff.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Saída estéreo em PCM float. A posição da cabeça de leitura é o relógio da música. */
class AudioTrackSink(sampleRate: Int) : AudioSink {
    private val track: AudioTrack

    init {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val minimum = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minimum * BUFFER_MULTIPLIER)
            .build()
    }

    override fun start() = track.play()

    override fun pause() = track.pause()

    override fun stop() {
        runCatching { track.stop() }
    }

    override fun write(buffer: FloatArray, frames: Int) {
        track.write(buffer, 0, frames * 2, AudioTrack.WRITE_BLOCKING)
    }

    override val playedFrames: Long get() = track.playbackHeadPosition.toLong() and UINT_MASK

    override fun release() = track.release()

    private companion object {
        const val BUFFER_MULTIPLIER = 2
        const val UINT_MASK = 0xFFFF_FFFFL
    }
}
