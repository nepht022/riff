package com.rodrigomachado.riff.core.data.importer

import com.rodrigomachado.riff.core.chart.ChartLoader
import com.rodrigomachado.riff.core.chart.LoadedChart
import com.rodrigomachado.riff.core.data.folder.FileTooLargeException
import com.rodrigomachado.riff.core.data.folder.FolderEntry
import com.rodrigomachado.riff.core.data.folder.FolderSource

/** Lê os arquivos de mapa de uma pasta (com limite de tamanho) e monta o [LoadedChart]. */
internal object ChartFolderReader {
    private val READ_FILES = listOf("notes.chart", "notes.mid", "song.ini")

    suspend fun read(source: FolderSource, children: List<FolderEntry>, maxBytes: Long): LoadedChart {
        val contents = mutableMapOf<String, ByteArray>()
        for (name in READ_FILES) {
            val entry = children.firstOrNull { !it.isDirectory && it.name.equals(name, ignoreCase = true) } ?: continue
            if (entry.sizeBytes > maxBytes) throw FileTooLargeException(entry.name, maxBytes)
            contents[name] = source.read(entry, maxBytes)
        }
        return ChartLoader.load { name -> contents[name.lowercase()] }
    }
}
