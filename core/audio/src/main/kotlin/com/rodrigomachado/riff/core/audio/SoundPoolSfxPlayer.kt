package com.rodrigomachado.riff.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

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
    private val missSounds = MISS_SOUNDS.map { pool.load(context, it, 1) }
    private val misses = MissSoundPicker(missSounds.size)
    private val handler = Handler(Looper.getMainLooper())
    private var missStream = 0
    private var missLevel = 0f
    private var missVolume = DEFAULT_VOLUME

    /** Um som de erro por vez: o novo encerra o anterior com um fade curto (spec da guitarra separada). */
    override fun miss() {
        val choice = misses.pick(SystemClock.uptimeMillis()) ?: return
        fadeOut(missStream, missLevel)
        missLevel = missVolume * choice.gain
        missStream = play(missSounds[choice.index], missLevel)
    }

    override fun setMissVolume(volume: Float) {
        missVolume = volume.coerceIn(0f, 1f)
    }

    override fun release() {
        handler.removeCallbacksAndMessages(null)
        pool.release()
    }

    private fun play(sound: Int, level: Float): Int = if (level > 0f) pool.play(sound, level, level, 1, 0, 1f) else 0

    private fun fadeOut(stream: Int, start: Float) {
        if (stream == 0) return
        for (step in 1..FADE_STEPS) {
            handler.postDelayed({
                val level = start * (1f - step.toFloat() / FADE_STEPS)
                pool.setVolume(stream, level, level)
                if (step == FADE_STEPS) pool.stop(stream)
            }, step * FADE_STEP_MS)
        }
    }

    private companion object {
        const val MAX_STREAMS = 6
        const val DEFAULT_VOLUME = 0.6f
        const val FADE_STEPS = 5
        const val FADE_STEP_MS = 5L // 5 passos de 5 ms: o fade de 25 ms
        val MISS_SOUNDS = listOf(
            R.raw.sfx_miss_1,
            R.raw.sfx_miss_2,
            R.raw.sfx_miss_3,
            R.raw.sfx_miss_4,
            R.raw.sfx_miss_5,
            R.raw.sfx_miss_6,
        )
    }
}
