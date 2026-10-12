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

        access.persistReadAccess("content://com.android.externalstorage.documents/tree/primary%3AVelha")
        access.persistReadAccess("content://com.android.externalstorage.documents/tree/primary%3ANova")

        assertEquals(
            listOf(Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ANova")),
            context.contentResolver.persistedUriPermissions.map { it.uri },
        )
    }
}
