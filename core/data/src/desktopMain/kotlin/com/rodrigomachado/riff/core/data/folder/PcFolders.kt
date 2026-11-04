package com.rodrigomachado.riff.core.data.folder

import com.rodrigomachado.riff.core.chart.sng.RandomRead
import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * O "URI" de uma pasta ou arquivo no PC: o caminho absoluto com `/` (o Java aceita no Windows, e as regras de
 * pasta da biblioteca, como [com.rodrigomachado.riff.core.data.library.LibraryFolders], comparam por `/`).
 */
fun pcUri(file: File): String = file.absoluteFile.path.replace('\\', '/')

/** Pastas do PC lidas com `java.io`. */
class FileFolderSource : FolderSource {
    override suspend fun listChildren(folderUri: String): List<FolderEntry> {
        val folder = File(folderUri)
        val children = folder.takeIf { it.isDirectory }?.listFiles()
            ?: throw FolderAccessLostException("a pasta $folderUri não existe mais ou não pode ser lida")
        return children.map { FolderEntry(pcUri(it), it.name, it.isDirectory, if (it.isFile) it.length() else 0) }
    }

    override suspend fun read(file: FolderEntry, maxBytes: Long): ByteArray {
        val source = File(file.uri)
        if (source.length() > maxBytes) throw FileTooLargeException(file.name, maxBytes)
        return source.readBytes()
    }
}

/** No PC não há permissão a guardar: a pasta escolhida continua acessível. */
class FileFolderAccess : FolderAccess {
    override fun persistAccess(folderUri: String) = Unit

    override fun canWrite(folderUri: String): Boolean = File(folderUri).canWrite()
}

/** Gravação nas pastas do PC (baixar e apagar mapas). */
class FileFolderWriter : FolderWriter {
    /**
     * Um arquivo novo com nome livre, como no celular (`SafFolderWriter.freeName`): com "Nome.sng" ou
     * "Nome.sng.part" ocupado, "Nome (2).sng.part", "Nome (3).sng.part"… O `createNewFile` é atômico: dois downloads
     * ao mesmo tempo nunca ficam com o mesmo arquivo (o que perde a corrida tenta o próximo nome).
     */
    override suspend fun createFile(parentUri: String, subfolder: String, name: String): String {
        val folder = File(parentUri, subfolder).apply { mkdirs() }
        val final = name.removeSuffix(PART)
        val base = final.substringBeforeLast('.')
        val extension = final.substringAfterLast('.', "")
        val file = generateSequence(1) { it + 1 }
            .take(MAX_COPIES)
            .map { n -> if (n == 1) final else "$base ($n).$extension" }
            .filterNot { File(folder, it).exists() || File(folder, it + PART).exists() }
            .map { File(folder, if (name.endsWith(PART)) it + PART else it) }
            .firstOrNull { it.createNewFile() }
            ?: throw IOException("não deu para criar $name em ${folder.path}")
        return pcUri(file)
    }

    override fun openOutput(fileUri: String): OutputStream = FileOutputStream(fileUri)

    override suspend fun nameOf(fileUri: String): String = File(fileUri).name

    /**
     * Renomeia sem nunca sobrescrever: com o destino ocupado, falha igual em todo sistema (o `renameTo` substituía
     * fora do Windows e falhava nele). O [createFile] já reservou o nome ("X.sng.part" ocupa "X.sng"), então só um
     * arquivo posto ali por fora do Riff chega aqui. Sem `ATOMIC_MOVE` de propósito: no Linux e no macOS ele vira o
     * `rename(2)`, que substitui o destino; sem opções, o Java confere e falha com [FileAlreadyExistsException].
     */
    override suspend fun rename(fileUri: String, newName: String): String {
        val file = File(fileUri)
        val target = File(file.parentFile, newName)
        Files.move(file.toPath(), target.toPath())
        return pcUri(target)
    }

    override suspend fun delete(fileUri: String) {
        File(fileUri).delete()
    }

    override suspend fun deleteDocument(fileUri: String): Boolean {
        val path = File(fileUri).toPath()
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return true
        return runCatching { deleteTree(path) }.isSuccess
    }

    /**
     * Apaga a pasta e o conteúdo sem entrar em links nem junções do Windows: o link sai, o que está do outro lado
     * fica (o `deleteRecursively` do Kotlin seguiria a junção e apagaria fora da biblioteca).
     */
    private fun deleteTree(path: Path) {
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (attributes.isDirectory && !attributes.isSymbolicLink && !attributes.isOther) {
            Files.list(path).use { children -> children.toList() }.forEach(::deleteTree)
        }
        Files.delete(path)
    }

    private companion object {
        const val PART = ".part"

        /** Um limite para o laço: "Nome (1000).sng" já é sinal de pasta estranha. */
        const val MAX_COPIES = 1000
    }
}

/** Arquivo do PC lido por posição (o `.sng` é lido aos pedaços, nunca inteiro na memória). */
class FileRandomReadOpener : RandomReadOpener {
    override fun open(uri: String): RandomRead {
        val file = RandomAccessFile(uri, "r")
        return object : RandomRead {
            override val size: Long = file.length()

            override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
                file.seek(position)
                return file.read(buffer, offset, length)
            }

            override fun close() = file.close()
        }
    }
}
