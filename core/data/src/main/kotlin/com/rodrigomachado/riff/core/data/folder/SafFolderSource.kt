package com.rodrigomachado.riff.core.data.folder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject

/**
 * Pastas por SAF com uma consulta por pasta (nome, tipo e tamanho de todos os filhos de uma vez).
 * Diferente do `DocumentFile.listFiles()`, os erros do provedor aparecem como exceção em vez de
 * virar uma pasta vazia, o que apagaria os mapas dela na limpeza da importação.
 */
class SafFolderSource @Inject constructor(@ApplicationContext private val context: Context) : FolderSource {

    @Suppress("TooGenericExceptionCaught") // provedores lançam tipos variados; providerFailure classifica
    override suspend fun listChildren(folderUri: String): List<FolderEntry> {
        val uri = Uri.parse(folderUri)
        val cursor = try {
            context.contentResolver.query(childrenUri(uri), PROJECTION, null, null, null)
        } catch (e: RuntimeException) {
            throw providerFailure(folderUri, e)
        } ?: throw FolderAccessLostException("sem acesso à pasta $folderUri")

        return cursor.use {
            val entries = mutableListOf<FolderEntry>()
            while (it.moveToNext()) {
                val id = it.getString(0)
                val name = it.getString(1).orEmpty()
                val isDirectory = it.getString(2) == Document.MIME_TYPE_DIR
                val size = if (it.isNull(3)) SIZE_UNKNOWN else it.getLong(3)
                val childUri = DocumentsContract.buildDocumentUriUsingTree(uri, id)
                entries += FolderEntry(childUri.toString(), name, isDirectory, size)
            }
            entries
        }
    }

    private fun childrenUri(uri: Uri): Uri {
        val documentId = if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.getDocumentId(uri)
        } else {
            DocumentsContract.getTreeDocumentId(uri)
        }
        return DocumentsContract.buildChildDocumentsUriUsingTree(uri, documentId)
    }

    /** Permissão revogada ou URI inválido viram "acesso perdido"; o resto é falha de leitura. */
    private fun providerFailure(folderUri: String, error: RuntimeException): Exception = when (error) {
        is SecurityException -> FolderAccessLostException("permissão revogada para $folderUri", error)
        is IllegalArgumentException -> FolderAccessLostException("pasta inválida ou apagada: $folderUri", error)
        else -> IOException("falha ao listar $folderUri: ${error.message}", error)
    }

    override suspend fun read(file: FolderEntry, maxBytes: Long): ByteArray {
        val input = context.contentResolver.openInputStream(Uri.parse(file.uri))
            ?: throw IOException("não foi possível abrir ${file.name}")
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_BYTES)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                // Lê no máximo maxBytes, mesmo quando o provedor não informa o tamanho.
                if (total > maxBytes) throw FileTooLargeException(file.name, maxBytes)
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    private companion object {
        val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
        )
        const val SIZE_UNKNOWN = 0L
        const val BUFFER_BYTES = 64 * 1024
    }
}

class SafFolderAccess @Inject constructor(@ApplicationContext private val context: Context) : FolderAccess {
    /** Guarda o acesso à pasta escolhida e libera o das anteriores (o Android limita quantas um app guarda). */
    override fun persistReadAccess(folderUri: String) {
        val chosen = Uri.parse(folderUri)
        val resolver = context.contentResolver
        resolver.takePersistableUriPermission(chosen, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        resolver.persistedUriPermissions
            .filter { it.uri != chosen }
            .forEach { resolver.releasePersistableUriPermission(it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}
