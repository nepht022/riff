package com.rodrigomachado.riff.feature.settings

import com.rodrigomachado.riff.core.audio.SfxPlayer
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository(initial: Settings = Settings.DEFAULT) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun update(transform: (Settings) -> Settings) {
        settings.value = transform(settings.value)
    }
}

class FakeSfxPlayer : SfxPlayer {
    val played = mutableListOf<String>()

    // Sem setter público: setMissVolume é o da interface.
    val missVolume: Float get() = lastMissVolume
    private var lastMissVolume = -1f
    var released = false

    override fun miss() {
        played += "miss"
    }

    override fun setMissVolume(volume: Float) {
        lastMissVolume = volume
    }

    override fun release() {
        released = true
    }
}
