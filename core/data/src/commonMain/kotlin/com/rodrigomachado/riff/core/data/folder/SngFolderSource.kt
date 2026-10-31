package com.rodrigomachado.riff.core.data.folder

import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
import com.rodrigomachado.riff.core.chart.sng.SngPackage
import com.rodrigomachado.riff.core.chart.sng.SngRef

/**
 * Um `.sng` lido como se fosse uma pasta: os arquivos de dentro aparecem como filhos, já desmascarados. Assim a
 * importação e o carregamento da partida tratam pacote e pasta do mesmo jeito.
 */
class SngFolderSource(private val delegate: FolderSource, private val opener: RandomReadOpener) : FolderSource {
    override suspend fun listChildren(folderUri: String): List<FolderEntry> {
        val file = SngRef.packageFileOf(folderUri) ?: return delegate.listChildren(folderUri)
        return open(file).use { sng ->
            val files = sng.files.map {
                FolderEntry(
                    SngRef.entryUri(file, it.name),
                    it.name,
                    isDirectory = false,
                    it.size,
                )
            }
            // O .sng guarda o song.ini nos metadados: vira um song.ini de dentro, como numa pasta comum.
            val ini = songIni(
                sng,
            )?.let { FolderEntry(SngRef.entryUri(file, SONG_INI), SONG_INI, false, it.size.toLong()) }
            files + listOfNotNull(ini)
        }
    }

    override suspend fun read(file: FolderEntry, maxBytes: Long): ByteArray {
        val (packageFile, name) = SngRef.entryOf(file.uri) ?: return delegate.read(file, maxBytes)
        return open(packageFile).use { sng ->
            if (name.equals(SONG_INI, ignoreCase = true)) songIni(sng)?.let { return@use it }
            val size = sng.files.firstOrNull { it.name.equals(name, ignoreCase = true) }?.size ?: 0
            if (size > maxBytes) throw FileTooLargeException(name, maxBytes)
            sng.read(name, maxBytes)
        }
    }

    private fun open(file: String): SngPackage = SngPackage.open(opener.open(file))

    /** Os metadados no formato do song.ini; null se o pacote tem o próprio song.ini ou não tem metadados. */
    private fun songIni(sng: SngPackage): ByteArray? {
        if (sng.metadata.isEmpty() || sng.files.any { it.name.equals(SONG_INI, ignoreCase = true) }) return null
        return ("[song]\n" + sng.metadata.entries.joinToString("\n") { "${it.key} = ${it.value}" }).toByteArray()
    }

    private companion object {
        const val SONG_INI = "song.ini"
    }
}
