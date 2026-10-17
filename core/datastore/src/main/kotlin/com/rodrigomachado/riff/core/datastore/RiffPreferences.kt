package com.rodrigomachado.riff.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Preferências do jogador e pasta da biblioteca. Valores inválidos no disco viram o padrão, nunca um crash. */
class RiffPreferences(private val dataStore: DataStore<Preferences>) {
    /** Arquivo ilegível vira preferências vazias (valores padrão) em vez de derrubar quem observa. */
    private val safeData: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val settings: Flow<Settings> = safeData.map { it.toSettings() }

    val libraryRootUri: Flow<String?> = safeData.map { it[LIBRARY_ROOT_URI] }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        dataStore.edit { prefs ->
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
            prefs[QUEUE_PREFETCH] = updated.queuePrefetch
            prefs[QUEUE_DELETE_UNPLAYED] = updated.queueDeleteUnplayed
            prefs[QUEUE_USE_MOBILE_DATA] = updated.queueUseMobileData
            prefs[QUEUE_REPLACE_SUGGESTED] = updated.queueReplaceSuggested
            prefs[QUEUE_CLOSEST_DURATION] = updated.queueClosestDuration
            prefs[SPOTIFY_NEXT_SONG_DIRECT] = updated.spotifyNextSongDirect
            prefs.remove(LEGACY_ASK_SEPARATION_ALWAYS)
            prefs.remove(LEGACY_SFX_VOLUME)
            prefs.remove(LEGACY_DEFAULT_DIFFICULTY)
        }
    }

    /** Nome de jogador do ranking e a chave deste celular (os dois juntos: um nome sem chave não vale nada). */
    val player: Flow<Pair<String?, String?>> = safeData.map { it[PLAYER_NAME] to it[PLAYER_KEY] }

    suspend fun setPlayer(name: String, key: String) {
        dataStore.edit {
            it[PLAYER_NAME] = name
            it[PLAYER_KEY] = key
        }
    }

    suspend fun setLibraryRootUri(uri: String) {
        dataStore.edit { it[LIBRARY_ROOT_URI] = uri }
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
            queuePrefetch = flag(QUEUE_PREFETCH, default.queuePrefetch),
            queueDeleteUnplayed = flag(QUEUE_DELETE_UNPLAYED, default.queueDeleteUnplayed),
            queueUseMobileData = flag(QUEUE_USE_MOBILE_DATA, default.queueUseMobileData),
            queueReplaceSuggested = flag(QUEUE_REPLACE_SUGGESTED, default.queueReplaceSuggested),
            queueClosestDuration = flag(QUEUE_CLOSEST_DURATION, default.queueClosestDuration),
            spotifyNextSongDirect = flag(SPOTIFY_NEXT_SONG_DIRECT, default.spotifyNextSongDirect),
        )
    }

    private fun Preferences.flag(key: Preferences.Key<Boolean>, default: Boolean): Boolean = this[key] ?: default

    private fun Preferences.volume(key: Preferences.Key<Float>): Float? =
        this[key]?.takeIf { !it.isNaN() }?.coerceIn(0f, 1f)

    private companion object {
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
        val SPOTIFY_NEXT_SONG_DIRECT = booleanPreferencesKey("spotify_next_song_direct")
        val LEGACY_SFX_VOLUME = floatPreferencesKey("sfx_volume")
        val LEGACY_DEFAULT_DIFFICULTY = stringPreferencesKey("default_difficulty")
        val LIBRARY_ROOT_URI = stringPreferencesKey("library_root_uri")
    }
}

/** Nome gravado de uma opção; um nome que não existe mais (versão antiga ou futura) volta ao padrão. */
private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
