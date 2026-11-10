package com.rodrigomachado.riff.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rodrigomachado.riff.core.model.ControlAction
import com.rodrigomachado.riff.core.model.Controls
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Preferências do jogador e pasta da biblioteca. Valores inválidos no disco viram o padrão, nunca um crash. */
@Suppress("TooManyFunctions") // um par ler/gravar por grupo de preferências (ajustes, ranking, Sala, pasta)
class RiffPreferences(private val dataStore: DataStore<Preferences>) {
    /** Arquivo ilegível vira preferências vazias (valores padrão) em vez de derrubar quem observa. */
    private val safeData: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val settings: Flow<Settings> = safeData.map { it.toSettings() }

    val libraryRootUri: Flow<String?> = safeData.map { it[LIBRARY_ROOT_URI] }

    /**
     * Grava tentando de novo: no Windows, trocar o arquivo recém-gravado às vezes falha por um instante (o antivírus
     * ou o indexador o seguram: "Unable to rename"). Na última tentativa, a falha segue para quem gravou.
     */
    private suspend fun editRetrying(transform: suspend (MutablePreferences) -> Unit) {
        repeat(WRITE_ATTEMPTS - 1) {
            try {
                dataStore.edit(transform)
                return
            } catch (_: IOException) {
                delay(WRITE_RETRY_MS)
            }
        }
        dataStore.edit(transform)
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        editRetrying { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[AUDIO_OFFSET_MS] = updated.audioOffsetMs
            prefs[NOTE_SPEED] = updated.noteSpeed
            prefs[LEFT_HANDED] = updated.leftHanded
            prefs[MASTER_VOLUME] = updated.masterVolume
            prefs[HIT_VOLUME] = updated.hitVolume
            prefs[MISS_VOLUME] = updated.missVolume
            prefs[MENU_SOUNDS] = updated.menuSounds
            prefs[ASK_SEPARATION_ALWAYS] = updated.askSeparationAlways
            prefs[HIGHWAY_BACKGROUND] = updated.highwayBackground.name
            prefs[HIGHWAY_ART] = updated.highwayArt.name
            prefs[NOTE_STYLE] = updated.noteStyle.name
            prefs[FRET_STYLE] = updated.fretStyle.name
            prefs[SHOW_CHART_IMAGE] = updated.showChartImage
            prefs[SEPARATE_ONLY_CHARGING] = updated.separateOnlyCharging
            prefs[CONTROLS_STRUM] = updated.controls.strum
            prefs[CONTROLS_KEYS] = encodeChoices(updated.controls.keys)
            prefs[CONTROLS_PAD] = encodeChoices(updated.controls.pad)
            prefs.remove(LEGACY_ASK_SEPARATION_ALWAYS)
            // As opções do Spotify (até a 0.4.0): o Spotify saiu do app.
            SPOTIFY_KEYS.forEach { prefs.remove(it) }
            prefs.remove(LEGACY_SFX_VOLUME)
            prefs.remove(LEGACY_DEFAULT_DIFFICULTY)
        }
    }

    /** Nome de jogador do ranking e a chave deste celular (os dois juntos: um nome sem chave não vale nada). */
    val player: Flow<Pair<String?, String?>> = safeData.map { it[PLAYER_NAME] to it[PLAYER_KEY] }

    /** Só a chave, antes da primeira reserva: se a resposta se perder, a nova tentativa usa a mesma. */
    suspend fun setPlayerKey(key: String) {
        editRetrying { it[PLAYER_KEY] = key }
    }

    /** O nome sumiu do ranking e outra pessoa pegou: a chave fica (é a mesma pessoa), o nome é pedido de novo. */
    suspend fun forgetPlayerName() {
        editRetrying { it.remove(PLAYER_NAME) }
    }

    /** Tirado do ranking pelo dono: nome e chave somem; um nome novo começa do zero, com outra chave. */
    suspend fun forgetPlayer() {
        editRetrying {
            it.remove(PLAYER_NAME)
            it.remove(PLAYER_KEY)
        }
    }

    suspend fun setPlayer(name: String, key: String) {
        editRetrying {
            it[PLAYER_NAME] = name
            it[PLAYER_KEY] = key
        }
    }

    /**
     * A identidade da Sala (login anônimo do Firebase): o `uid` e o `refreshToken`, os dois juntos (um sem o outro
     * não vale nada). O `idToken` não é guardado: vence em 1 h e é renovado na memória.
     */
    val roomIdentity: Flow<Pair<String?, String?>> = safeData.map { it[ROOM_UID] to it[ROOM_REFRESH_TOKEN] }

    suspend fun setRoomIdentity(uid: String, refreshToken: String) {
        editRetrying {
            it[ROOM_UID] = uid
            it[ROOM_REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun setLibraryRootUri(uri: String) {
        editRetrying { it[LIBRARY_ROOT_URI] = uri }
    }

    private fun Preferences.toSettings(): Settings {
        val default = Settings.DEFAULT
        // O volume único de antes (sfx_volume) vale para acerto e erro até o jogador mexer neles.
        val legacyEffects = volume(LEGACY_SFX_VOLUME)
        return Settings(
            audioOffsetMs = this[AUDIO_OFFSET_MS] ?: default.audioOffsetMs,
            noteSpeed = this[NOTE_SPEED]?.takeIf { it.isFinite() && it > 0f }
                ?.coerceIn(Settings.MIN_NOTE_SPEED, Settings.MAX_NOTE_SPEED)
                ?: default.noteSpeed,
            leftHanded = flag(LEFT_HANDED, default.leftHanded),
            masterVolume = volume(MASTER_VOLUME) ?: default.masterVolume,
            hitVolume = volume(HIT_VOLUME) ?: legacyEffects ?: default.hitVolume,
            missVolume = volume(MISS_VOLUME) ?: legacyEffects ?: default.missVolume,
            menuSounds = flag(MENU_SOUNDS, default.menuSounds),
            askSeparationAlways = flag(ASK_SEPARATION_ALWAYS, default.askSeparationAlways),
            highwayBackground = enumOr(this[HIGHWAY_BACKGROUND], default.highwayBackground),
            highwayArt = enumOr(this[HIGHWAY_ART], default.highwayArt),
            noteStyle = enumOr(this[NOTE_STYLE], default.noteStyle),
            fretStyle = enumOr(this[FRET_STYLE], default.fretStyle),
            showChartImage = flag(SHOW_CHART_IMAGE, default.showChartImage),
            separateOnlyCharging = flag(SEPARATE_ONLY_CHARGING, default.separateOnlyCharging),
            controls = controls(),
        )
    }

    /**
     * Cada ação lida sobre o padrão: ação desconhecida, valor ilegível ou ausente ficam com o padrão daquela ação.
     * As escolhas entram pela mesma troca da tela, então uma tecla ou um botão repetido no disco não vale por dois.
     */
    private fun Preferences.controls(): Controls {
        val keys = decodeChoices(this[CONTROLS_KEYS]) { it.toLongOrNull() }
        val pad = decodeChoices(this[CONTROLS_PAD]) { value -> value.takeIf { PAD_BUTTON.matches(it) } }
        val strum = flag(CONTROLS_STRUM, Settings.DEFAULT.controls.strum)
        val chosen = keys.fold(Controls(strum = strum)) { controls, (action, key) -> controls.withKey(action, key) }
        return pad.fold(chosen) { controls, (action, button) -> controls.withPad(action, button) }
    }

    private fun Preferences.flag(key: Preferences.Key<Boolean>, default: Boolean): Boolean = this[key] ?: default

    private fun Preferences.volume(key: Preferences.Key<Float>): Float? =
        this[key]?.takeIf { !it.isNaN() }?.coerceIn(0f, 1f)

    private companion object {
        /** Três tentativas, 50 ms entre elas: o arquivo costuma ser liberado em poucos milissegundos. */
        const val WRITE_ATTEMPTS = 3
        const val WRITE_RETRY_MS = 50L

        val AUDIO_OFFSET_MS = intPreferencesKey("audio_offset_ms")
        val NOTE_SPEED = floatPreferencesKey("note_speed")
        val LEFT_HANDED = booleanPreferencesKey("left_handed")
        val MASTER_VOLUME = floatPreferencesKey("master_volume")
        val HIT_VOLUME = floatPreferencesKey("hit_volume")
        val MISS_VOLUME = floatPreferencesKey("miss_volume")
        val MENU_SOUNDS = booleanPreferencesKey("menu_sounds")

        // A v2 nasceu ligada para todos; a chave antiga (desligada de fábrica) só é apagada.
        val ASK_SEPARATION_ALWAYS = booleanPreferencesKey("ask_separation_always_v2")
        val LEGACY_ASK_SEPARATION_ALWAYS = booleanPreferencesKey("ask_separation_always")
        val HIGHWAY_BACKGROUND = stringPreferencesKey("highway_background")
        val HIGHWAY_ART = stringPreferencesKey("highway_art")
        val NOTE_STYLE = stringPreferencesKey("note_style")
        val FRET_STYLE = stringPreferencesKey("fret_style")
        val SHOW_CHART_IMAGE = booleanPreferencesKey("show_chart_image")
        val QUEUE_PREFETCH = booleanPreferencesKey("queue_prefetch")
        val QUEUE_DELETE_UNPLAYED = booleanPreferencesKey("queue_delete_unplayed")
        val QUEUE_USE_MOBILE_DATA = booleanPreferencesKey("queue_use_mobile_data")
        val QUEUE_REPLACE_SUGGESTED = booleanPreferencesKey("queue_replace_suggested")
        val QUEUE_CLOSEST_DURATION = booleanPreferencesKey("queue_closest_duration")
        val PLAYER_NAME = stringPreferencesKey("player_name")
        val PLAYER_KEY = stringPreferencesKey("player_key")
        val ROOM_UID = stringPreferencesKey("room_uid")
        val ROOM_REFRESH_TOKEN = stringPreferencesKey("room_refresh_token")
        val SPOTIFY_NEXT_SONG_DIRECT = booleanPreferencesKey("spotify_next_song_direct")
        val SPOTIFY_KEYS = listOf(
            QUEUE_PREFETCH,
            QUEUE_DELETE_UNPLAYED,
            QUEUE_USE_MOBILE_DATA,
            QUEUE_REPLACE_SUGGESTED,
            QUEUE_CLOSEST_DURATION,
            SPOTIFY_NEXT_SONG_DIRECT,
        )
        val SEPARATE_ONLY_CHARGING = booleanPreferencesKey("separate_only_charging")
        val CONTROLS_STRUM = booleanPreferencesKey("controls_strum")

        // Texto `AÇÃO=valor;…`: o `Key.keyCode` (FRET_1=4294967361;…) e o botão (FRET_1=m:3;…).
        val CONTROLS_KEYS = stringPreferencesKey("controls_keys")
        val CONTROLS_PAD = stringPreferencesKey("controls_pad")

        /** Botão do layout do GLFW (`m:3`), cru (`r:12`) ou o chapéu (`h:up`/`h:down`). */
        val PAD_BUTTON = Regex("[mr]:\\d{1,3}|h:(up|down)")
        val LEGACY_SFX_VOLUME = floatPreferencesKey("sfx_volume")
        val LEGACY_DEFAULT_DIFFICULTY = stringPreferencesKey("default_difficulty")
        val LIBRARY_ROOT_URI = stringPreferencesKey("library_root_uri")
    }
}

/** Nome gravado de uma opção; um nome que não existe mais (versão antiga ou futura) volta ao padrão. */
private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

private fun <V> encodeChoices(choices: Map<ControlAction, V>): String =
    choices.entries.joinToString(";") { (action, value) -> "${action.name}=$value" }

/** Os pares legíveis de `AÇÃO=valor;…`, na ordem gravada; o resto é ignorado (fica o padrão). */
private fun <V : Any> decodeChoices(text: String?, parse: (String) -> V?): List<Pair<ControlAction, V>> =
    text.orEmpty().split(';').mapNotNull { entry ->
        val action = ControlAction.entries.firstOrNull { it.name == entry.substringBefore('=', "") }
        val value = parse(entry.substringAfter('=', ""))
        if (action != null && value != null) action to value else null
    }
