package com.rodrigomachado.riff.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.audio.SfxPlayer
import com.rodrigomachado.riff.core.audio.SfxPlayerFactory
import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.model.SeparationUsage
import com.rodrigomachado.riff.core.model.Settings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@Suppress("TooManyFunctions") // uma função por preferência da tela, mais as prévias dos sons
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val separations: AiSeparationRepository,
    sfxPlayers: SfxPlayerFactory,
) : ViewModel() {
    /** `null` enquanto as preferências carregam. */
    val uiState: StateFlow<Settings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // Criado já na abertura: o SoundPool carrega os sons aos poucos e a primeira prévia precisa deles prontos.
    private val sfx: SfxPlayer = sfxPlayers.create()
    private var released = false

    // O último valor pedido: a gravação é assíncrona e a prévia logo depois precisa do volume novo.
    private var latest: Settings? = null

    /** Quantas músicas a IA separou e o espaço que ocupam. */
    val separationUsage: StateFlow<SeparationUsage> = separations.usage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SeparationUsage(0, 0))

    fun deleteSeparations() {
        viewModelScope.launch { separations.deleteAll() }
    }

    fun setAskSeparationAlways(always: Boolean) = save { it.copy(askSeparationAlways = always) }

    fun setMasterVolume(volume: Float) = save { it.copy(masterVolume = volume.coerceIn(0f, 1f)) }

    fun setHitVolume(volume: Float) = save { it.copy(hitVolume = volume.coerceIn(0f, 1f)) }

    fun setMissVolume(volume: Float) = save { it.copy(missVolume = volume.coerceIn(0f, 1f)) }

    fun setNoteSpeed(speed: Float) = save {
        it.copy(noteSpeed = speed.coerceIn(Settings.MIN_NOTE_SPEED, Settings.MAX_NOTE_SPEED))
    }

    fun setLeftHanded(leftHanded: Boolean) = save { it.copy(leftHanded = leftHanded) }

    fun setMenuSounds(enabled: Boolean) = save { it.copy(menuSounds = enabled) }

    fun previewHit() = preview { it.hit(PREVIEW_LANE) }

    fun previewMiss() = preview { it.miss() }

    private fun save(change: (Settings) -> Settings) {
        val current = latest ?: uiState.value ?: Settings.DEFAULT
        latest = change(current)
        viewModelScope.launch { repository.update(change) }
    }

    private fun preview(play: (SfxPlayer) -> Unit) {
        if (released) return
        val settings = latest ?: uiState.value ?: return
        sfx.setVolumes(settings.effectiveHitVolume, settings.effectiveMissVolume)
        play(sfx)
    }

    internal fun release() {
        if (released) return
        released = true
        sfx.release()
    }

    override fun onCleared() = release()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val PREVIEW_LANE = 2 // a nota do meio (amarela)
    }
}
