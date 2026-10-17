package com.rodrigomachado.riff.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rodrigomachado.riff.core.model.FretStyle
import com.rodrigomachado.riff.core.model.HighwayArt
import com.rodrigomachado.riff.core.model.HighwayBackground
import com.rodrigomachado.riff.core.model.NoteStyle
import com.rodrigomachado.riff.core.model.Settings
import com.rodrigomachado.riff.core.model.TrackLook
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
    fun askingAlwaysToSeparateStartsOnAndCanBeTurnedOff() = runTest {
        val preferences = RiffPreferences(InMemoryPreferencesStore())
        assertEquals(true, preferences.settings.first().askSeparationAlways)

        preferences.updateSettings { it.copy(askSeparationAlways = false) }

        assertEquals(false, preferences.settings.first().askSeparationAlways)
    }

    @Test
    fun theTrackLookIsSavedByName() = runTest {
        val preferences = RiffPreferences(InMemoryPreferencesStore())

        preferences.updateSettings {
            it.copy(
                highwayBackground = HighwayBackground.NEON,
                highwayArt = HighwayArt.WINGED_SKULL,
                noteStyle = NoteStyle.SKULLS,
                fretStyle = FretStyle.SPEAKERS,
                showChartImage = false,
            )
        }

        val settings = preferences.settings.first()
        assertEquals(
            TrackLook(HighwayBackground.NEON, HighwayArt.WINGED_SKULL, NoteStyle.SKULLS, FretStyle.SPEAKERS),
            settings.trackLook,
        )
        assertEquals(false, settings.showChartImage)
    }

    @Test
    fun anOptionThatNoLongerExistsFallsBackToTheDefault() = runTest {
        val store = InMemoryPreferencesStore()
        store.edit { it[stringPreferencesKey("highway_background")] = "DISCO_BALL" }

        assertEquals(HighwayBackground.STAGE, RiffPreferences(store).settings.first().highwayBackground)
    }

    @Test
    fun askingAlwaysIsOnEvenIfTheOldPreferenceWasSavedOff() = runTest {
        val store = InMemoryPreferencesStore()
        store.edit { it[booleanPreferencesKey("ask_separation_always")] = false }

        assertEquals(true, RiffPreferences(store).settings.first().askSeparationAlways)
    }

    @Test
    fun queueOptionsAreSaved() = runTest {
        val preferences = RiffPreferences(InMemoryPreferencesStore())

        preferences.updateSettings {
            it.copy(
                queuePrefetch = true,
                queueDeleteUnplayed = false,
                queueUseMobileData = true,
                queueReplaceSuggested = false,
            )
        }

        val settings = preferences.settings.first()
        assertEquals(true, settings.queuePrefetch)
        assertEquals(false, settings.queueDeleteUnplayed)
        assertEquals(true, settings.queueUseMobileData)
        assertEquals(false, settings.queueReplaceSuggested)
    }
}
