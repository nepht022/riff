package com.rodrigomachado.riff.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class SoundPoolSfxPlayer(context: Context) : SfxPlayer {
    private val pool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val hitSound = pool.load(context, R.raw.sfx_hit, 1)
    private val missSound = pool.load(context, R.raw.sfx_miss, 1)
    private var volume = DEFAULT_VOLUME

    override fun hit() = play(hitSound)

    override fun miss() = play(missSound)

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
    }

    override fun release() = pool.release()

    private fun play(sound: Int) {
        if (volume > 0f) pool.play(sound, volume, volume, 1, 0, 1f)
    }

    private companion object {
        const val MAX_STREAMS = 6
        const val DEFAULT_VOLUME = 0.6f
    }
}
