package com.rodrigomachado.riff.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * DataStore em memória: o teste cobre a conversão das preferências, não a gravação em disco
 * (que no Windows, onde o Gradle roda nesta máquina, falha ao renomear o arquivo temporário).
 */
private class InMemoryPreferencesStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

class RiffPreferencesTest {
    @Test
    fun emptyStoreGivesDefaults() = runTest {
        val preferences = RiffPreferences(InMemoryPreferencesStore())
        assertEquals(Settings.DEFAULT, preferences.settings.first())
        assertNull(preferences.libraryRootUri.first())
    }

    @Test
    fun updatesArePersisted() = runTest {
        val preferences = RiffPreferences(InMemoryPreferencesStore())

        preferences.updateSettings {
            it.copy(
                audioOffsetMs = -180,
                leftHanded = true,
                noteSpeed = 1.5f,
                masterVolume = 0.8f,
                hitVolume = 0.2f,
                missVolume = 0.9f,
            )
        }

        val settings = preferences.settings.first()
        assertEquals(-180, settings.audioOffsetMs)
        assertEquals(true, settings.leftHanded)
        assertEquals(1.5f, settings.noteSpeed)
        assertEquals(0.8f, settings.masterVolume)
        assertEquals(0.2f, settings.hitVolume)
        assertEquals(0.9f, settings.missVolume)
    }

    @Test
    fun menuSoundsStartOnAndCanBeTurnedOff() = runTest {
        val preferences = RiffPreferences(InMemoryPreferencesStore())
        assertEquals(true, preferences.settings.first().menuSounds)

        preferences.updateSettings { it.copy(menuSounds = false) }

        assertEquals(false, preferences.settings.first().menuSounds)
    }

    @Test
    fun theOldSingleEffectsVolumeCarriesOverToHitAndMiss() = runTest {
        val store = InMemoryPreferencesStore()
        store.edit { it[floatPreferencesKey("sfx_volume")] = 0.25f }

        val settings = RiffPreferences(store).settings.first()

        assertEquals(0.25f, settings.hitVolume)
        assertEquals(0.25f, settings.missVolume)
        assertEquals(1f, settings.masterVolume)
    }

    @Test
    fun corruptedValuesFallBackToDefaultsInsteadOfCrashing() = runTest {
        val store = InMemoryPreferencesStore()
        store.edit {
            it[floatPreferencesKey("hit_volume")] = 5f
            it[floatPreferencesKey("miss_volume")] = Float.NaN
            it[floatPreferencesKey("master_volume")] = -2f
            it[floatPreferencesKey("note_speed")] = 9f
        }

        val settings = RiffPreferences(store).settings.first()
        assertEquals(1f, settings.hitVolume)
        assertEquals(Settings.DEFAULT.missVolume, settings.missVolume)
        assertEquals(0f, settings.masterVolume)
        assertEquals(Settings.MAX_NOTE_SPEED, settings.noteSpeed)
    }

    @Test
    fun unreadableStoreGivesDefaultsInsteadOfCrashing() = runTest {
        val broken = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = kotlinx.coroutines.flow.flow {
                throw java.io.IOException(
                    "arquivo corrompido",
                )
            }

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = error(
                "não usado",
            )
        }
        val preferences = RiffPreferences(broken)
        assertEquals(Settings.DEFAULT, preferences.settings.first())
        assertNull(preferences.libraryRootUri.first())
    }

    @Test
    fun askingAlwaysToSeparateStartsOffAndCanBeTurnedOn() = runTest {
        val preferences = RiffPreferences(InMemoryPreferencesStore())
        assertEquals(false, preferences.settings.first().askSeparationAlways)

        preferences.updateSettings { it.copy(askSeparationAlways = true) }

        assertEquals(true, preferences.settings.first().askSeparationAlways)
    }
}
