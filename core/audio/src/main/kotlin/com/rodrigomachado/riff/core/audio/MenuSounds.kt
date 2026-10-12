package com.rodrigomachado.riff.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.rodrigomachado.riff.core.model.MenuSound
import kotlin.random.Random

/** Sons de guitarra dos botões do menu. */
fun interface MenuSoundPlayer {
    /** Toca o som do papel [sound] em [volume] (0 a 1; 0 não toca). */
    fun play(sound: MenuSound, volume: Float)
}

/**
 * Navegar e confirmar sorteiam entre duas gravações; voltar tem uma. Os sons são carregados uma vez e ficam na
 * memória enquanto o app vive (são poucos e curtos).
 */
class SoundPoolMenuSounds(context: Context) : MenuSoundPlayer {
    private val pool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val picker = MenuSoundPicker(
        mapOf(
            MenuSound.NAVIGATE to listOf(R.raw.sfx_menu_navigate_1, R.raw.sfx_menu_navigate_2),
            MenuSound.CONFIRM to listOf(R.raw.sfx_menu_confirm_1, R.raw.sfx_menu_confirm_2),
            MenuSound.BACK to listOf(R.raw.sfx_menu_back),
        ).mapValues { (_, sounds) -> sounds.map { pool.load(context, it, 1) } },
        Random.Default,
    )

    override fun play(sound: MenuSound, volume: Float) {
        val level = volume.coerceIn(0f, 1f)
        if (level > 0f) pool.play(picker.pick(sound), level, level, 1, 0, 1f)
    }

    private companion object {
        const val MAX_STREAMS = 3
    }
}
