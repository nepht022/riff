package com.rodrigomachado.riff.core.data.folder

/** Arquivo ou subpasta dentro da pasta da biblioteca. */
data class FolderEntry(val uri: String, val name: String, val isDirectory: Boolean, val sizeBytes: Long)

/** Leitura de pastas. No Android é SAF; nos testes, uma árvore em memória. */
interface FolderSource {
    /** @throws FolderAccessLostException se a pasta não puder mais ser lida (permissão revogada ou pasta apagada). */
    suspend fun listChildren(folderUri: String): List<FolderEntry>

    /** Lê no máximo [maxBytes]; acima disso lança [FileTooLargeException] sem carregar o resto. */
    suspend fun read(file: FolderEntry, maxBytes: Long): ByteArray
}

class FileTooLargeException(name: String, maxBytes: Long) :
    java.io.IOException("$name grande demais (mais de ${maxBytes / (1024 * 1024)} MB)")

/** Guarda a permissão de leitura da pasta escolhida para valer depois que o app reiniciar. */
interface FolderAccess {
    fun persistReadAccess(folderUri: String)
}

class FolderAccessLostException(message: String, cause: Throwable? = null) : Exception(message, cause)
