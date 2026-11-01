package com.rodrigomachado.riff.core.data.folder

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

/** Grava na pasta da biblioteca pelo SAF (a pasta precisa ter sido escolhida com permissão de escrita). */
class SafFolderWriter @Inject constructor(@ApplicationContext private val context: Context) : FolderWriter {
    private val resolver get() = context.contentResolver

    override suspend fun createFile(parentUri: String, subfolder: String, name: String): String =
        withContext(Dispatchers.IO) {
            val tree = Uri.parse(parentUri)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val folder = children(tree, parent)[subfolder]
                ?: DocumentsContract.createDocument(resolver, parent, Document.MIME_TYPE_DIR, subfolder)
                ?: throw IOException("não deu para criar $subfolder")
            val free = freeName(tree, folder, name)
            (
                DocumentsContract.createDocument(resolver, folder, MIME_BINARY, free)
                    ?: throw IOException("não deu para criar $free")
                ).toString()
        }

    override fun openOutput(fileUri: String): OutputStream =
        resolver.openOutputStream(Uri.parse(fileUri), "w") ?: throw IOException("não deu para gravar $fileUri")

    override suspend fun nameOf(fileUri: String): String = withContext(Dispatchers.IO) {
        resolver.query(Uri.parse(fileUri), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: throw IOException("sem nome para $fileUri")
    }

    override suspend fun rename(fileUri: String, newName: String): String = withContext(Dispatchers.IO) {
        (
            DocumentsContract.renameDocument(resolver, Uri.parse(fileUri), newName)
                ?: throw IOException("não deu para renomear para $newName")
            ).toString()
    }

    override suspend fun deleteDocument(fileUri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            DocumentsContract.deleteDocument(resolver, Uri.parse(fileUri))
        } catch (@Suppress("SwallowedException") e: java.io.FileNotFoundException) {
            true // já não existe
        } catch (@Suppress("SwallowedException") e: SecurityException) {
            false
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            false
        }
    }

    override suspend fun delete(fileUri: String) {
        withContext(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(resolver, Uri.parse(fileUri)) } }
    }

    /** Filhos de [folder] (documento dentro da árvore [tree]): nome → URI. */
    private fun children(tree: Uri, folder: Uri): Map<String, Uri> {
        val query = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(folder))
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME)
        return resolver.query(query, projection, null, null, null)?.use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    put(
                        cursor.getString(1).orEmpty(),
                        DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)),
                    )
                }
            }
        } ?: throw FolderAccessLostException("sem acesso a $folder")
    }

    /** "Nome.sng.part" livre: com o nome final ocupado, "Nome (2).sng.part", "Nome (3).sng.part"… */
    private fun freeName(tree: Uri, folder: Uri, name: String): String {
        val taken = children(tree, folder).keys.map { it.removeSuffix(PART) }.toSet()
        val final = name.removeSuffix(PART)
        val base = final.substringBeforeLast('.')
        val extension = final.substringAfterLast('.', "")
        val free = generateSequence(1) { it + 1 }
            .map { n -> if (n == 1) final else "$base ($n).$extension" }
            .first { it !in taken }
        return if (name.endsWith(PART)) free + PART else free
    }

    private companion object {
        const val MIME_BINARY = "application/octet-stream"
        const val PART = ".part"
    }
}
