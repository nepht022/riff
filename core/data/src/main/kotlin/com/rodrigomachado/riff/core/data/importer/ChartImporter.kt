package com.rodrigomachado.riff.core.data.importer

import com.rodrigomachado.riff.core.chart.ChartLoader
import com.rodrigomachado.riff.core.chart.ChartParseException
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.folder.FolderEntry
import com.rodrigomachado.riff.core.data.folder.FolderSource
import com.rodrigomachado.riff.core.model.Chart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLDecoder

data class ImportFailure(val folderName: String, val reason: String)

data class ImportReport(val importedCount: Int, val failures: List<ImportFailure>)

interface ChartImporter {
    /**
     * Varre a pasta [rootUri] e as subpastas, importa cada pasta de música e apaga da biblioteca
     * os mapas cujas pastas sumiram. [onProgress] recebe quantas pastas de música já foram lidas.
     *
     * @throws FolderAccessLostException se a raiz não puder ser lida; a biblioteca fica como estava.
     */
    suspend fun import(rootUri: String, onProgress: (scannedFolders: Int) -> Unit = {}): ImportReport
}

class FolderChartImporter(
    private val source: FolderSource,
    private val repository: ChartRepository,
    private val dispatcher: CoroutineDispatcher,
    private val now: () -> Long,
) : ChartImporter {

    override suspend fun import(rootUri: String, onProgress: (Int) -> Unit): ImportReport = withContext(dispatcher) {
        val scan = Scan(scanId = now(), onProgress = onProgress)
        val rootChildren = source.listChildren(rootUri)
        scan.visit(
            FolderEntry(rootUri, folderNameOf(rootUri), isDirectory = true, sizeBytes = 0),
            rootChildren,
            depth = 0,
        )
        repository.removeNotSeenIn(scan.scanId)
        ImportReport(scan.imported, scan.failures)
    }

    private inner class Scan(val scanId: Long, val onProgress: (Int) -> Unit) {
        var imported = 0
        var scanned = 0
        val failures = mutableListOf<ImportFailure>()

        suspend fun visit(folder: FolderEntry, children: List<FolderEntry>, depth: Int) {
            if (children.any { !it.isDirectory && it.name.lowercase() in NOTES_FILES }) {
                importSong(folder, children)
                return
            }
            if (depth >= MAX_DEPTH) return
            for (child in children.filter { it.isDirectory }) {
                val grandChildren = try {
                    source.listChildren(child.uri)
                } catch (e: FolderAccessLostException) {
                    failures += ImportFailure(child.name, "sem acesso à pasta: ${e.message}")
                    continue
                }
                visit(child, grandChildren, depth + 1)
            }
        }

        private suspend fun importSong(folder: FolderEntry, children: List<FolderEntry>) {
            scanned++
            onProgress(scanned)
            try {
                repository.saveScanned(load(folder, children), scanId)
                imported++
            } catch (e: ChartParseException) {
                failures += ImportFailure(folder.name, e.message ?: "mapa inválido")
            } catch (e: IOException) {
                failures += ImportFailure(folder.name, "não foi possível ler: ${e.message}")
            }
        }
    }

    private suspend fun load(folder: FolderEntry, children: List<FolderEntry>): Chart {
        val contents = mutableMapOf<String, ByteArray>()
        for (name in READ_FILES) {
            val entry = children.firstOrNull { !it.isDirectory && it.name.equals(name, ignoreCase = true) } ?: continue
            checkSize(entry.name, entry.sizeBytes)
            val bytes = source.read(entry)
            checkSize(entry.name, bytes.size.toLong())
            contents[name] = bytes
        }
        val loaded = ChartLoader.load { name -> contents[name.lowercase()] }
        return Chart(
            id = loaded.id,
            title = loaded.info.title ?: folder.name,
            artist = loaded.info.artist.orEmpty(),
            album = loaded.info.album,
            charter = loaded.info.charter,
            durationMs = loaded.info.songLengthMs,
            folderUri = folder.uri,
            format = loaded.format,
            difficulties = loaded.tracks.keys,
        )
    }

    private fun checkSize(name: String, sizeBytes: Long) {
        if (sizeBytes > MAX_FILE_BYTES) {
            val megabytes = sizeBytes / BYTES_PER_MB
            throw ChartParseException("$name grande demais ($megabytes MB)")
        }
    }

    companion object {
        const val MAX_FILE_BYTES = 16L * 1024 * 1024
        const val MAX_DEPTH = 8
        private const val BYTES_PER_MB = 1024L * 1024
        private val NOTES_FILES = setOf("notes.chart", "notes.mid")
        private val READ_FILES = listOf("notes.chart", "notes.mid", "song.ini")

        /** Nome legível da raiz a partir do URI do SAF (`.../tree/primary%3ACharts` vira `Charts`). */
        private fun folderNameOf(uri: String): String =
            URLDecoder.decode(uri.substringAfterLast('/'), "UTF-8").substringAfterLast('/').substringAfterLast(':')
    }
}
