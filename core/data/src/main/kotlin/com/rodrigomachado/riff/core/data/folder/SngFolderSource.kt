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
            sng.files.map { FolderEntry(SngRef.entryUri(file, it.name), it.name, isDirectory = false, it.size) }
        }
    }

    override suspend fun read(file: FolderEntry, maxBytes: Long): ByteArray {
        val (packageFile, name) = SngRef.entryOf(file.uri) ?: return delegate.read(file, maxBytes)
        return open(packageFile).use { sng ->
            val size = sng.files.firstOrNull { it.name.equals(name, ignoreCase = true) }?.size ?: 0
            if (size > maxBytes) throw FileTooLargeException(name, maxBytes)
            sng.read(name, maxBytes)
        }
    }

    private fun open(file: String): SngPackage = SngPackage.open(opener.open(file))
}
