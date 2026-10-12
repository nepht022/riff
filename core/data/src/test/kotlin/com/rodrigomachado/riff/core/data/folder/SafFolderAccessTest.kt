package com.rodrigomachado.riff.core.data.folder

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SafFolderAccessTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun choosingANewFolderReleasesTheOldOnes() {
        val access = SafFolderAccess(context)

        access.persistAccess("content://com.android.externalstorage.documents/tree/primary%3AVelha")
        access.persistAccess("content://com.android.externalstorage.documents/tree/primary%3ANova")

        assertEquals(
            listOf(Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ANova")),
            context.contentResolver.persistedUriPermissions.map { it.uri },
        )
    }

    @Test
    fun theChosenFolderCanBeWrittenTo() {
        val access = SafFolderAccess(context)
        val folder = "content://com.android.externalstorage.documents/tree/primary%3ACharts"

        access.persistAccess(folder)

        // Baixar mapas grava "Baixados pelo Riff" dentro dela.
        assertEquals(true, access.canWrite(folder))
        assertEquals(false, access.canWrite("content://com.android.externalstorage.documents/tree/primary%3AOutra"))
    }
}
