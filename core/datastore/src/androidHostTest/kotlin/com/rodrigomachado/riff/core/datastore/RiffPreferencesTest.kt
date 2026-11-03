package com.rodrigomachado.riff.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rodrigomachado.riff.core.model.ControlAction
import com.rodrigomachado.riff.core.model.Controls
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
    fun controlsSurviveSavingAndReading() = runTest {
        val preferences = RiffPreferences(InMemoryPreferencesStore())
        val controls = Controls(strum = false)
            .withKey(ControlAction.FRET_1, Controls.desktopKey(81)) // Q
            .withPad(ControlAction.STRUM_UP, "r:7")

        preferences.updateSettings { it.copy(controls = controls) }

        assertEquals(controls, preferences.settings.first().controls)
    }

    /** Texto estragado ou de uma versão futura: cada ação ilegível (ou que falta) fica com o seu padrão. */
    @Test
    fun anUnreadableValueFallsBackToItsDefault() = runTest {
        val store = InMemoryPreferencesStore()
        store.edit {
            it[stringPreferencesKey("controls_keys")] = "FRET_1=xx;FOO=3;FRET_2=${Controls.desktopKey(81)};lixo"
            it[stringPreferencesKey("controls_pad")] = "FRET_3=;PAUSE=r:9;=m:1"
        }

        val controls = RiffPreferences(store).settings.first().controls

        assertEquals(Controls.DEFAULT_KEYS + (ControlAction.FRET_2 to Controls.desktopKey(81)), controls.keys)
        assertEquals(Controls.DEFAULT_PAD + (ControlAction.PAUSE to "r:9"), controls.pad)
        assertEquals(true, controls.strum)
    }

    /** Uma tecla gravada para duas ações (texto mexido à mão) não vale por duas: a leitura troca, como a escolha. */
    @Test
    fun aKeySavedForTwoActionsIsNotUsedTwice() = runTest {
        val store = InMemoryPreferencesStore()
        // A, a tecla padrão do FRET_1.
        store.edit { it[stringPreferencesKey("controls_keys")] = "FRET_2=${Controls.desktopKey(65)}" }

        val keys = RiffPreferences(store).settings.first().controls.keys

        assertEquals(Controls.desktopKey(65), keys[ControlAction.FRET_2])
        assertEquals(Controls.desktopKey(83), keys[ControlAction.FRET_1])
        assertEquals(ControlAction.entries.size, keys.values.toSet().size)
    }

    @Test
    fun askingAlwaysIsOnEvenIfTheOldPreferenceWasSavedOff() = runTest {
        val store = InMemoryPreferencesStore()
        store.edit { it[booleanPreferencesKey("ask_separation_always")] = false }

        assertEquals(true, RiffPreferences(store).settings.first().askSeparationAlways)
    }

    @Test
    fun separatingOnlyWhileChargingIsSavedAndStartsOff() = runTest {
        val preferences = RiffPreferences(InMemoryPreferencesStore())
        assertEquals(false, preferences.settings.first().separateOnlyCharging)

        preferences.updateSettings { it.copy(separateOnlyCharging = true) }

        assertEquals(true, preferences.settings.first().separateOnlyCharging)
    }

    /** A 0.4.0 gravou as opções do Spotify: a leitura não quebra e a próxima gravação as esquece. */
    @Test
    fun theSpotifyKeysAreForgotten() = runTest {
        val store = InMemoryPreferencesStore()
        val queuePrefetch = androidx.datastore.preferences.core.booleanPreferencesKey("queue_prefetch")
        val nextSongDirect = androidx.datastore.preferences.core.booleanPreferencesKey("spotify_next_song_direct")
        val leftHanded = androidx.datastore.preferences.core.booleanPreferencesKey("left_handed")
        store.updateData {
            it.toMutablePreferences().apply {
                this[queuePrefetch] = true
                this[nextSongDirect] = false
                this[leftHanded] = true
            }
        }
        val preferences = RiffPreferences(store)

        assertEquals(true, preferences.settings.first().leftHanded)
        preferences.updateSettings { it }

        val saved = store.data.first()
        assertEquals(null, saved[queuePrefetch])
        assertEquals(null, saved[nextSongDirect])
        assertEquals(true, saved[leftHanded])
    }
}
