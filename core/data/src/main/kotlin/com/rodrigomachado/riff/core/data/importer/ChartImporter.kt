package com.rodrigomachado.riff.core.data.importer

import com.rodrigomachado.riff.core.chart.ChartParseException
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.folder.FileTooLargeException
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.folder.FolderEntry
import com.rodrigomachado.riff.core.data.folder.FolderSource
import com.rodrigomachado.riff.core.model.Chart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLDecoder

/** Por que uma pasta não entrou na biblioteca. A tela mostra o tipo no idioma do aparelho. */
enum class ImportFailureKind { INVALID_CHART, FILE_TOO_LARGE, UNREADABLE, NO_ACCESS, UNEXPECTED, DUPLICATE }

/**
 * Pasta que não entrou. [detail] é técnico (linha do erro no mapa, mensagem do sistema) ou, em [ImportFailureKind.DUPLICATE],
 * o nome da pasta que já tem o mesmo mapa.
 */
data class ImportFailure(val folderName: String, val kind: ImportFailureKind, val detail: String? = null)

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
    private val maxFileBytes: Long,
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

        /** Id (conteúdo) de cada mapa já importado nesta varredura, com a pasta dele. */
        private val seen = mutableMapOf<String, String>()

        suspend fun visit(folder: FolderEntry, children: List<FolderEntry>, depth: Int) {
            if (children.any { !it.isDirectory && it.name.lowercase() in NOTES_FILES }) {
                importSong(folder, children)
                return
            }
            if (depth >= MAX_DEPTH) return
            for (child in children.filter { it.isDirectory }) {
                val grandChildren = listOrKeep(child) ?: continue
                visit(child, grandChildren, depth + 1)
            }
        }

        /** Lista a subpasta; se não der, registra a falha, mantém o que já existia dela e devolve null. */
        private suspend fun listOrKeep(folder: FolderEntry): List<FolderEntry>? = try {
            source.listChildren(folder.uri)
        } catch (e: FolderAccessLostException) {
            keep(folder, ImportFailureKind.NO_ACCESS, e.message)
            null
        } catch (e: IOException) {
            keep(folder, ImportFailureKind.UNREADABLE, e.message)
            null
        }

        /** Pasta que falhou: registra no relatório e mantém o que já estava importado dela. */
        private suspend fun keep(folder: FolderEntry, kind: ImportFailureKind, detail: String?) {
            failures += ImportFailure(folder.name, kind, detail)
            repository.keepFolder(folder.uri, scanId)
        }

        @Suppress("TooGenericExceptionCaught") // qualquer erro de uma pasta vira falha no relatório, não crash
        private suspend fun importSong(folder: FolderEntry, children: List<FolderEntry>) {
            scanned++
            onProgress(scanned)
            try {
                val chart = load(folder, children)
                val original = seen.putIfAbsent(chart.id, folder.name)
                if (original != null) {
                    // Mesmo conteúdo em outra pasta: o mapa já está na biblioteca; conta uma vez só.
                    failures += ImportFailure(folder.name, ImportFailureKind.DUPLICATE, original)
                    return
                }
                repository.saveScanned(chart, scanId)
                imported++
            } catch (e: CancellationException) {
                throw e
            } catch (e: ChartParseException) {
                keep(folder, ImportFailureKind.INVALID_CHART, e.message)
            } catch (e: FileTooLargeException) {
                keep(folder, ImportFailureKind.FILE_TOO_LARGE, e.message)
            } catch (e: IOException) {
                keep(folder, ImportFailureKind.UNREADABLE, e.message)
            } catch (e: Exception) {
                keep(folder, ImportFailureKind.UNEXPECTED, "${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private suspend fun load(folder: FolderEntry, children: List<FolderEntry>): Chart {
        val loaded = ChartFolderReader.read(source, children, maxFileBytes)
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

    companion object {
        const val MAX_FILE_BYTES = 16L * 1024 * 1024
        const val MAX_DEPTH = 8
        private val NOTES_FILES = setOf("notes.chart", "notes.mid")

        /** Nome legível da raiz a partir do URI do SAF (`.../tree/primary%3ACharts` vira `Charts`). */
        private fun folderNameOf(uri: String): String =
            URLDecoder.decode(uri.substringAfterLast('/'), "UTF-8").substringAfterLast('/').substringAfterLast(':')
    }
}
