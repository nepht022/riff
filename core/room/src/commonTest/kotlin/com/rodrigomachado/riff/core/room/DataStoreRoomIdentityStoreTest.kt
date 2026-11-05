package com.rodrigomachado.riff.core.room

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.rodrigomachado.riff.core.datastore.RiffPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** DataStore em memória (como no teste do RiffPreferences). */
private class InMemoryPreferencesStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

class DataStoreRoomIdentityStoreTest {
    @Test
    fun identitySurvivesAndStartsEmpty() = runTest {
        val preferences = RiffPreferences(InMemoryPreferencesStore())
        val store = DataStoreRoomIdentityStore(preferences)
        assertNull(store.read())

        store.write(RoomIdentity("u1", "r1"))

        assertEquals(RoomIdentity("u1", "r1"), DataStoreRoomIdentityStore(preferences).read())
    }
}
