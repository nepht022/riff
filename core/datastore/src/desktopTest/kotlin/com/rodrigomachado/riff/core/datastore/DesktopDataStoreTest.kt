package com.rodrigomachado.riff.core.datastore

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DesktopDataStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun theSettingsSurviveReopening() = runTest {
        val file = File(folder.root, "riff_settings.preferences_pb")
        val first = SupervisorJob()
        RiffPreferences(riffDataStore(file, CoroutineScope(Dispatchers.IO + first))).updateSettings {
            it.copy(missVolume = 0.25f)
        }
        first.cancelAndJoin() // fecha o primeiro, como ao sair do Riff

        val reopened = RiffPreferences(riffDataStore(file)).settings.first()

        assertEquals(0.25f, reopened.missVolume)
    }
}
