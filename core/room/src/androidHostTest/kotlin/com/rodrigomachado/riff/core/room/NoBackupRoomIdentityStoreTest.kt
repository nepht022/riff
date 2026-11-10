package com.rodrigomachado.riff.core.room

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * A identidade da Sala no celular fica na pasta `no_backup` (o Android nunca a leva no backup nem na troca de
 * celular): restaurado noutro aparelho, o app começa outra conta anônima em vez de dividir o uid com o antigo. As
 * preferências do resto (o nome, os ajustes) continuam no `riff_settings`, que vai no backup.
 */
@RunWith(RobolectricTestRunner::class)
class NoBackupRoomIdentityStoreTest {
    @Test
    fun identityLivesOutsideTheBackup() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val store = noBackupRoomIdentityStore(context)
        assertNull(store.read())

        store.write(RoomIdentity("u1", "r1"))

        assertEquals(RoomIdentity("u1", "r1"), store.read())
        val file = File(context.noBackupFilesDir, "riff_room.preferences_pb")
        assertTrue("a identidade não está em $file", file.exists())
        assertFalse(File(context.filesDir, "datastore/riff_settings.preferences_pb").exists())
    }
}
