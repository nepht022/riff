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

/** Guarda a permissão da pasta escolhida (leitura e, se o provedor deixar, escrita) para depois que o app reiniciar. */
interface FolderAccess {
    fun persistAccess(folderUri: String)

    /** A pasta foi escolhida com permissão de gravar (para baixar mapas nela). */
    fun canWrite(folderUri: String): Boolean
}

/** Gravação na pasta da biblioteca (baixar mapas). */
interface FolderWriter {
    /** Cria [name] em [subfolder] (criada se faltar) dentro da pasta [parentUri]; devolve o URI do arquivo. */
    suspend fun createFile(parentUri: String, subfolder: String, name: String): String

    fun openOutput(fileUri: String): java.io.OutputStream

    /** O nome do arquivo como a pasta mostra (o URI do SAF vem codificado e nem sempre traz o nome). */
    suspend fun nameOf(fileUri: String): String

    /** Renomeia e devolve o URI novo. */
    suspend fun rename(fileUri: String, newName: String): String

    /** Apaga, sem erro se já não existir. */
    suspend fun delete(fileUri: String)
}

class FolderAccessLostException(message: String, cause: Throwable? = null) : Exception(message, cause)
