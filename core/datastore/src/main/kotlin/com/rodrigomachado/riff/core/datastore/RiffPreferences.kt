package com.rodrigomachado.riff.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Preferências do jogador e pasta da biblioteca. Valores inválidos no disco viram o padrão, nunca um crash. */
class RiffPreferences(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<Settings> = dataStore.data.map { it.toSettings() }

    val libraryRootUri: Flow<String?> = dataStore.data.map { it[LIBRARY_ROOT_URI] }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[AUDIO_OFFSET_MS] = updated.audioOffsetMs
            prefs[NOTE_SPEED] = updated.noteSpeed
            prefs[LEFT_HANDED] = updated.leftHanded
            prefs[DEFAULT_DIFFICULTY] = updated.defaultDifficulty.name
            prefs[SFX_VOLUME] = updated.sfxVolume
        }
    }

    suspend fun setLibraryRootUri(uri: String) {
        dataStore.edit { it[LIBRARY_ROOT_URI] = uri }
    }

    private fun Preferences.toSettings(): Settings {
        val default = Settings.DEFAULT
        return Settings(
            audioOffsetMs = this[AUDIO_OFFSET_MS] ?: default.audioOffsetMs,
            noteSpeed = this[NOTE_SPEED]?.takeIf { it.isFinite() && it > 0f } ?: default.noteSpeed,
            leftHanded = this[LEFT_HANDED] ?: default.leftHanded,
            defaultDifficulty = this[DEFAULT_DIFFICULTY]
                ?.let { name -> Difficulty.entries.firstOrNull { it.name == name } }
                ?: default.defaultDifficulty,
            sfxVolume = this[SFX_VOLUME]?.takeIf { !it.isNaN() }?.coerceIn(0f, 1f) ?: default.sfxVolume,
        )
    }

    private companion object {
        val AUDIO_OFFSET_MS = intPreferencesKey("audio_offset_ms")
        val NOTE_SPEED = floatPreferencesKey("note_speed")
        val LEFT_HANDED = booleanPreferencesKey("left_handed")
        val DEFAULT_DIFFICULTY = stringPreferencesKey("default_difficulty")
        val SFX_VOLUME = floatPreferencesKey("sfx_volume")
        val LIBRARY_ROOT_URI = stringPreferencesKey("library_root_uri")
    }
}
