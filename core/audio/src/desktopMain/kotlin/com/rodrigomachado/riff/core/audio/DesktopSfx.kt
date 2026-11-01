package com.rodrigomachado.riff.core.audio

import com.rodrigomachado.riff.core.model.MenuSound
import java.io.BufferedInputStream
import java.io.InputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.log10
import kotlin.random.Random

/** Os WAV do app (os mesmos de `res/raw` do celular, servidos ao PC como recursos). */
object DesktopSounds {
    val HITS = (1..5).map { "sfx_hit_$it.wav" }
    val MISSES = (1..6).map { "sfx_miss_$it.wav" }
    val MENU = mapOf(
        MenuSound.NAVIGATE to listOf("sfx_menu_navigate_1.wav", "sfx_menu_navigate_2.wav"),
        MenuSound.CONFIRM to listOf("sfx_menu_confirm_1.wav", "sfx_menu_confirm_2.wav"),
        MenuSound.BACK to listOf("sfx_menu_back.wav"),
    )
    val ALL = HITS + MISSES + MENU.values.flatten()

    fun open(name: String): InputStream = BufferedInputStream(
        checkNotNull(DesktopSounds::class.java.getResourceAsStream("/$name")) { "som $name ausente" },
    )
}

/**
 * Um som carregado uma vez e tocado de novo do começo a cada vez. Sem saída de som no PC, fica mudo (os sons são
 * enfeite: a partida segue sem eles).
 */
internal class LoadedSound(name: String, openClip: () -> Clip) {
    private val clip: Clip? = runCatching {
        openClip().apply { AudioSystem.getAudioInputStream(DesktopSounds.open(name)).use { open(it) } }
    }.getOrNull()

    fun play(level: Float) {
        val sound = clip ?: return
        if (level <= 0f) return
        sound.stop()
        sound.framePosition = 0
        sound.gain(level)
        sound.start()
    }

    fun stop() {
        clip?.stop()
    }

    fun close() {
        clip?.close()
    }

    private fun Clip.gain(level: Float) {
        if (!isControlSupported(FloatControl.Type.MASTER_GAIN)) return
        val control = getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
        control.value = (DECIBELS_PER_DECADE * log10(level)).coerceIn(control.minimum, control.maximum)
    }

    private companion object {
        const val DECIBELS_PER_DECADE = 20f
    }
}

/** Sons de acerto e de erro no PC, com o mesmo sorteio do celular. */
class ClipSfxPlayer(openClip: () -> Clip = AudioSystem::getClip) : SfxPlayer {
    private val hits = DesktopSounds.HITS.map { LoadedSound(it, openClip) }
    private val misses = DesktopSounds.MISSES.map { LoadedSound(it, openClip) }
    private val picker = MissSoundPicker(misses.size)
    private var lastMiss: LoadedSound? = null
    private var hitVolume = DEFAULT_VOLUME
    private var missVolume = DEFAULT_VOLUME

    override fun hit(lane: Int) = hits[lane.coerceIn(hits.indices)].play(hitVolume)

    /** Um som de erro por vez: o novo encerra o anterior. */
    override fun miss() {
        val choice = picker.pick(uptimeMs()) ?: return
        lastMiss?.stop()
        lastMiss = misses[choice.index].also { it.play(missVolume * choice.gain) }
    }

    override fun setVolumes(hit: Float, miss: Float) {
        hitVolume = hit.coerceIn(0f, 1f)
        missVolume = miss.coerceIn(0f, 1f)
    }

    override fun release() = (hits + misses).forEach { it.close() }

    private companion object {
        const val DEFAULT_VOLUME = 0.6f
    }
}

/** Sons dos botões do menu no PC (carregados uma vez, como no celular). */
class ClipMenuSounds(openClip: () -> Clip = AudioSystem::getClip) : MenuSoundPlayer {
    private val sounds = DesktopSounds.MENU.mapValues { (_, names) -> names.map { LoadedSound(it, openClip) } }

    override fun play(sound: MenuSound, volume: Float) {
        sounds.getValue(sound).random(Random.Default).play(volume.coerceIn(0f, 1f))
    }
}
