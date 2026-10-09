package com.rodrigomachado.riff.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack

/**
 * Saída estéreo em PCM float. O relógio da música são os quadros que já saíram no alto-falante
 * (`getTimestamp`, que desconta a latência de saída, como o MediaPlayer faz). Ver [PresentedFrames].
 */
class AudioTrackSink(private val sampleRate: Int) : AudioSink {
    private val track: AudioTrack
    private val timestamp = AudioTimestamp()
    private val presented = PresentedFrames(sampleRate)

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

    override fun start() {
        presented.started(System.nanoTime())
        track.play()
    }

    override fun pause() {
        track.pause()
        presented.paused()
    }

    override fun stop() {
        runCatching { track.stop() }
    }

    override fun write(buffer: FloatArray, offsetFrames: Int, frames: Int): Int =
        maxOf(0, track.write(buffer, offsetFrames * 2, frames * 2, AudioTrack.WRITE_BLOCKING)) / 2

    override val playedFrames: Long
        get() {
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) return presented.frames
            val head = track.playbackHeadPosition.toLong() and UINT_MASK
            val stamped = track.getTimestamp(timestamp)
            return presented.update(
                head = head,
                stampFrame = if (stamped) timestamp.framePosition else null,
                stampNs = if (stamped) timestamp.nanoTime else null,
                nowNs = System.nanoTime(),
            )
        }

    override val isPresenting: Boolean get() = presented.isPresenting

    override fun release() = track.release()

    private companion object {
        const val BUFFER_MULTIPLIER = 2
        const val UINT_MASK = 0xFFFF_FFFFL
    }
}
