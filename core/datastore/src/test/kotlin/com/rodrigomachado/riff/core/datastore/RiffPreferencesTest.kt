package com.rodrigomachado.riff.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rodrigomachado.riff.core.model.Difficulty
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
            it.copy(audioOffsetMs = -180, leftHanded = true, defaultDifficulty = Difficulty.EXPERT, sfxVolume = 0.25f)
        }
        preferences.setLibraryRootUri("content://pasta")

        val settings = preferences.settings.first()
        assertEquals(-180, settings.audioOffsetMs)
        assertEquals(true, settings.leftHanded)
        assertEquals(Difficulty.EXPERT, settings.defaultDifficulty)
        assertEquals(0.25f, settings.sfxVolume)
        assertEquals("content://pasta", preferences.libraryRootUri.first())
    }

    @Test
    fun corruptedValuesFallBackToDefaultsInsteadOfCrashing() = runTest {
        val store = InMemoryPreferencesStore()
        store.edit {
            it[floatPreferencesKey("sfx_volume")] = 5f
            it[floatPreferencesKey("note_speed")] = -1f
            it[stringPreferencesKey("default_difficulty")] = "IMPOSSIVEL"
        }

        val settings = RiffPreferences(store).settings.first()
        assertEquals(1f, settings.sfxVolume)
        assertEquals(Settings.DEFAULT.noteSpeed, settings.noteSpeed)
        assertEquals(Settings.DEFAULT.defaultDifficulty, settings.defaultDifficulty)
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
}
