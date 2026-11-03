package com.rodrigomachado.riff.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * No Windows, trocar o arquivo das preferências às vezes falha por um instante (o antivírus ou o indexador segurando o
 * arquivo recém-gravado: "Unable to rename"). A gravação tenta de novo em vez de perder a escolha.
 */
class WriteRetryTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** O DataStore de verdade, com as primeiras [failures] gravações falhando como a troca de arquivo no Windows. */
    private class FlakyStore(private val real: DataStore<Preferences>, var failures: Int) : DataStore<Preferences> {
        override val data: Flow<Preferences> = real.data

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (failures > 0) {
                failures--
                throw IOException("Unable to rename riff_settings.preferences_pb.tmp")
            }
            return real.updateData(transform)
        }
    }

    @Test
    fun aWriteThatFailsForAMomentIsTriedAgain(): Unit = runBlocking {
        val file = File(folder.root, "riff_settings.preferences_pb")
        val real = riffDataStore(file, CoroutineScope(Dispatchers.IO + SupervisorJob()))
        val store = FlakyStore(real, failures = 2)
        val preferences = RiffPreferences(store)

        preferences.updateSettings { it.copy(missVolume = 0.25f) }

        assertEquals(0.25f, preferences.settings.first().missVolume)
    }
}
