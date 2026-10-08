package com.rodrigomachado.riff.core.data.folder

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject

class SafFolderSource @Inject constructor(@ApplicationContext private val context: Context) : FolderSource {
    override suspend fun listChildren(folderUri: String): List<FolderEntry> {
        val folder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
        if (folder == null || !folder.isDirectory || !folder.canRead()) {
            throw FolderAccessLostException("sem acesso à pasta $folderUri")
        }
        return try {
            folder.listFiles().map { FolderEntry(it.uri.toString(), it.name.orEmpty(), it.isDirectory, it.length()) }
        } catch (e: SecurityException) {
            throw FolderAccessLostException("permissão revogada para $folderUri", e)
        }
    }

    override suspend fun read(file: FolderEntry): ByteArray =
        context.contentResolver.openInputStream(Uri.parse(file.uri))?.use { it.readBytes() }
            ?: throw IOException("não foi possível abrir ${file.name}")
}

class SafFolderAccess @Inject constructor(@ApplicationContext private val context: Context) : FolderAccess {
    override fun persistReadAccess(folderUri: String) {
        context.contentResolver.takePersistableUriPermission(
            Uri.parse(folderUri),
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}
