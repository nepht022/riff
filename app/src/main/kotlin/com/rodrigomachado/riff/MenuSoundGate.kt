package com.rodrigomachado.riff

import com.rodrigomachado.riff.core.audio.MenuSoundPlayer
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.Settings

/** Toca o som do menu no volume geral, só com "Sons do menu" ligado (e as preferências já lidas). */
fun playMenuSound(player: MenuSoundPlayer, settings: Settings?, sound: MenuSound) {
    if (settings == null || !settings.menuSounds) return
    player.play(sound, settings.masterVolume)
}

/** O jogador acabou de ligar "Sons do menu": um toque confirma que voltaram (o interruptor não soa sozinho). */
fun menuSoundsTurnedOn(previous: Settings?, current: Settings?): Boolean =
    previous != null && !previous.menuSounds && current?.menuSounds == true
