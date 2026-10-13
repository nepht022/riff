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
            prefs.remove(LEGACY_HIT_VOLUME)
            prefs[MISS_VOLUME] = updated.missVolume
            prefs[MENU_SOUNDS] = updated.menuSounds
            prefs.remove(LEGACY_SFX_VOLUME)
            prefs.remove(LEGACY_DEFAULT_DIFFICULTY)
        }
    }

    suspend fun setLibraryRootUri(uri: String) {
        dataStore.edit { it[LIBRARY_ROOT_URI] = uri }
    }

    private fun Preferences.toSettings(): Settings {
        val default = Settings.DEFAULT
        // O volume único de antes (sfx_volume) vale para o erro até o jogador mexer nele.
        val legacyEffects = volume(LEGACY_SFX_VOLUME)
        return Settings(
            audioOffsetMs = this[AUDIO_OFFSET_MS] ?: default.audioOffsetMs,
            noteSpeed = this[NOTE_SPEED]?.takeIf { it.isFinite() && it > 0f }
                ?.coerceIn(Settings.MIN_NOTE_SPEED, Settings.MAX_NOTE_SPEED)
                ?: default.noteSpeed,
            leftHanded = this[LEFT_HANDED] ?: default.leftHanded,
            masterVolume = volume(MASTER_VOLUME) ?: default.masterVolume,
            missVolume = volume(MISS_VOLUME) ?: legacyEffects ?: default.missVolume,
            menuSounds = this[MENU_SOUNDS] ?: default.menuSounds,
        )
    }

    private fun Preferences.volume(key: Preferences.Key<Float>): Float? =
        this[key]?.takeIf { !it.isNaN() }?.coerceIn(0f, 1f)

    private companion object {
        val AUDIO_OFFSET_MS = intPreferencesKey("audio_offset_ms")
        val NOTE_SPEED = floatPreferencesKey("note_speed")
        val LEFT_HANDED = booleanPreferencesKey("left_handed")
        val MASTER_VOLUME = floatPreferencesKey("master_volume")

        // O som de acerto saiu do app (2026-10-13): o valor antigo é ignorado e apagado na próxima gravação.
        val LEGACY_HIT_VOLUME = floatPreferencesKey("hit_volume")
        val MISS_VOLUME = floatPreferencesKey("miss_volume")
        val MENU_SOUNDS = booleanPreferencesKey("menu_sounds")
        val LEGACY_SFX_VOLUME = floatPreferencesKey("sfx_volume")
        val LEGACY_DEFAULT_DIFFICULTY = stringPreferencesKey("default_difficulty")
        val LIBRARY_ROOT_URI = stringPreferencesKey("library_root_uri")
    }
}
