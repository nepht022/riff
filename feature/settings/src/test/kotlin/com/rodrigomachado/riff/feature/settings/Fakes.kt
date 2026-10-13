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
    var hitVolume = -1f
    var missVolume = -1f
    var released = false

    override fun hit(lane: Int) {
        played += "hit:$lane"
    }

    override fun miss() {
        played += "miss"
    }

    override fun setVolumes(hit: Float, miss: Float) {
        hitVolume = hit
        missVolume = miss
    }

    override fun release() {
        released = true
    }
}
