package com.rodrigomachado.riff.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.audio.SfxPlayer
import com.rodrigomachado.riff.core.audio.SfxPlayerFactory
import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.AppLanguageStore
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.ranking.RankingRepository
import com.rodrigomachado.riff.core.model.AppLanguage
import com.rodrigomachado.riff.core.model.SeparationUsage
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.model.TrackLook
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** As Configurações; no celular o [HiltSettingsViewModel] é o criado pelo Hilt. */
@Suppress("TooManyFunctions") // uma função por preferência da tela (o visual da pista é uma só), mais as prévias
open class SettingsViewModel(
    private val repository: SettingsRepository,
    private val separations: AiSeparationRepository,
    private val languages: AppLanguageStore,
    ranking: RankingRepository,
    sfxPlayers: SfxPlayerFactory,
) : ViewModel() {
    /** O nome no ranking; `null` sem o ranking no build (a linha some). */
    val rankingName: StateFlow<RankingName?> =
        (if (ranking.isConfigured) ranking.player.map { RankingName(it.name) } else flowOf(null))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

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

    /** Troca o idioma do jogo; a tela é refeita já no idioma novo. */
    fun setLanguage(language: AppLanguage) = languages.set(language)

    fun deleteSeparations() {
        viewModelScope.launch { separations.deleteAll() }
    }

    fun setAskSeparationAlways(always: Boolean) = save { it.copy(askSeparationAlways = always) }

    fun setSeparateOnlyCharging(onlyCharging: Boolean) = save { it.copy(separateOnlyCharging = onlyCharging) }

    fun setMasterVolume(volume: Float) = save { it.copy(masterVolume = volume.coerceIn(0f, 1f)) }

    fun setMissVolume(volume: Float) = save { it.copy(missVolume = volume.coerceIn(0f, 1f)) }

    fun setNoteSpeed(speed: Float) = save {
        it.copy(noteSpeed = speed.coerceIn(Settings.MIN_NOTE_SPEED, Settings.MAX_NOTE_SPEED))
    }

    fun setLeftHanded(leftHanded: Boolean) = save { it.copy(leftHanded = leftHanded) }

    fun setMenuSounds(enabled: Boolean) = save { it.copy(menuSounds = enabled) }

    /** Aplica a escolha sobre o valor gravado agora, não sobre o que a tela mostrava. */
    fun setTrackLook(change: (TrackLook) -> TrackLook) = save {
        val look = change(it.trackLook)
        it.copy(
            highwayBackground = look.background,
            highwayArt = look.art,
            noteStyle = look.notes,
            fretStyle = look.frets,
        )
    }

    fun setShowChartImage(show: Boolean) = save { it.copy(showChartImage = show) }

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
    }
}
