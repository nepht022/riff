package com.rodrigomachado.riff.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.audio.SfxPlayer
import com.rodrigomachado.riff.core.audio.SfxPlayerFactory
import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.AppLanguageStore
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.model.AppLanguage
import com.rodrigomachado.riff.core.model.SeparationUsage
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.model.TrackLook
import com.rodrigomachado.riff.core.spotify.web.SpotifyWebAuth
import com.rodrigomachado.riff.core.spotify.web.WebAuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@Suppress("TooManyFunctions") // uma função por preferência da tela (o visual da pista é uma só), mais as prévias
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val separations: AiSeparationRepository,
    private val languages: AppLanguageStore,
    private val webAuth: SpotifyWebAuth,
    folders: LibraryFolderStore,
    folderAccess: FolderAccess,
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

    private val refused = MutableStateFlow(false)

    /** Recusa da autorização (nesta tela) ou autorização perdida (o Spotify pediu de novo). */
    val queueMessage: StateFlow<QueueMessage?> = combine(refused, webAuth.state) { refusedNow, auth ->
        when {
            refusedNow -> QueueMessage.REFUSED
            auth == WebAuthState.LOST -> QueueMessage.LOST
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** A fila só baixa com permissão de gravar na pasta da biblioteca. */
    val canWriteToLibrary: StateFlow<Boolean> = folders.rootUri
        .map { root -> root != null && folderAccess.canWrite(root) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    fun onQueue(action: QueueAction) = when (action) {
        is QueueAction.Prefetch -> setQueuePrefetch(action.on)
        is QueueAction.DeleteUnplayed -> setQueueDeleteUnplayed(action.on)
        is QueueAction.UseMobileData -> setQueueUseMobileData(action.on)
        is QueueAction.ReplaceSuggested -> setQueueReplaceSuggested(action.replace)
        is QueueAction.ClosestDuration -> save { it.copy(queueClosestDuration = action.download) }
        is QueueAction.NextSongDirect -> save { it.copy(spotifyNextSongDirect = action.direct) }
    }

    /** Ligar pede a autorização do Spotify na primeira vez (ou depois de perdida); recusada, continua desligada. */
    fun setQueuePrefetch(on: Boolean) {
        if (!on || webAuth.state.value == WebAuthState.AUTHORIZED) {
            save { it.copy(queuePrefetch = on) }
            return
        }
        viewModelScope.launch {
            val authorized = webAuth.authorize()
            refused.value = !authorized
            if (authorized) save { it.copy(queuePrefetch = true) }
        }
    }

    fun setQueueDeleteUnplayed(on: Boolean) = save { it.copy(queueDeleteUnplayed = on) }

    fun setQueueUseMobileData(on: Boolean) = save { it.copy(queueUseMobileData = on) }

    fun setQueueReplaceSuggested(replace: Boolean) = save { it.copy(queueReplaceSuggested = replace) }

    /** Troca o idioma do jogo; o Android refaz a tela já no idioma novo. */
    fun setLanguage(language: AppLanguage) = languages.set(language)

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

    fun setTrackLook(look: TrackLook) = save {
        it.copy(
            highwayBackground = look.background,
            highwayArt = look.art,
            noteStyle = look.notes,
            fretStyle = look.frets,
        )
    }

    fun setShowChartImage(show: Boolean) = save { it.copy(showChartImage = show) }

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
