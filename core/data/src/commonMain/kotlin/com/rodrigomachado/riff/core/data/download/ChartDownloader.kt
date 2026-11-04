package com.rodrigomachado.riff.core.data.download

import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
import com.rodrigomachado.riff.core.chart.sng.SngPackage
import com.rodrigomachado.riff.core.data.EncoreDownloads
import com.rodrigomachado.riff.core.data.IoDispatcher
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.download.ChartDownloadException.Reason
import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.data.folder.FolderWriter
import com.rodrigomachado.riff.core.data.importer.ChartImporter
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.encore.EncoreClient
import com.rodrigomachado.riff.core.model.Chart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Subpasta da biblioteca onde os mapas baixados ficam. */
const val DOWNLOADS_FOLDER = "Baixados pelo Riff"

class ChartDownloadException(val reason: Reason, cause: Throwable?) : Exception(reason.name, cause) {
    enum class Reason { NO_FOLDER, NO_WRITE_ACCESS, NETWORK, BROKEN_FILE, STORAGE }
}

interface ChartDownloader {
    /**
     * Baixa [chart] para "Baixados pelo Riff" e o põe na biblioteca.
     * Qualquer falha (e sair no meio) apaga o arquivo provisório.
     *
     * @throws ChartDownloadException com o motivo para a tela.
     */
    suspend fun download(chart: EncoreChart, onProgress: (Float) -> Unit): Chart
}

class EncoreChartDownloader @Inject constructor(
    private val encore: EncoreClient,
    private val folders: LibraryFolderStore,
    private val access: FolderAccess,
    private val writer: FolderWriter,
    private val importer: ChartImporter,
    private val downloads: EncoreDownloads,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    private val opener: RandomReadOpener,
) : ChartDownloader {
    @Suppress("TooGenericExceptionCaught") // qualquer saída (erro ou cancelamento) apaga o provisório
    override suspend fun download(chart: EncoreChart, onProgress: (Float) -> Unit): Chart =
        withContext(dispatcher) {
            val root = folders.rootUri.first() ?: throw ChartDownloadException(Reason.NO_FOLDER, null)
            if (!access.canWrite(root)) throw ChartDownloadException(Reason.NO_WRITE_ACCESS, null)
            var current = failing(Reason.STORAGE) { writer.createFile(root, DOWNLOADS_FOLDER, fileName(chart) + PART) }
            try {
                failing(Reason.NETWORK) { writer.openOutput(current).use { encore.download(chart, it, onProgress) } }
                failing(Reason.BROKEN_FILE) { SngPackage.open(opener.open(current)).close() }
                val name = failing(Reason.STORAGE) { writer.nameOf(current) }.removeSuffix(PART)
                current = failing(Reason.STORAGE) { writer.rename(current, name) }
                val imported = failing(Reason.BROKEN_FILE) { importer.importPackage(current, name) }
                downloads.remember(imported.id, chart.md5)
                imported
            } catch (e: Throwable) {
                withContext(NonCancellable) { writer.delete(current) }
                throw e
            }
        }

    /** Cada etapa vira um motivo na tela; o cancelamento (sair da tela) passa direto. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> failing(reason: Reason, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ChartDownloadException) {
        throw e
    } catch (e: Exception) {
        throw ChartDownloadException(reason, e)
    }

    private fun fileName(chart: EncoreChart): String = downloadFileName(chart.artist, chart.name)

    private companion object {
        const val PART = ".part"
    }
}

/**
 * "Artista - Nome.sng", sem os caracteres que as pastas não aceitam (os do Windows e os de controle). Um nome que o
 * Windows reserva para dispositivos (CON, NUL, COM1…) ganha um "_" na frente: o Windows lê só até o primeiro ponto,
 * e "Nul. - CON.sng" seria o NUL. Os outros nomes ficam como sempre foram.
 */
internal fun downloadFileName(artist: String, name: String): String {
    val base = "$artist - $name".replace(INVALID, "_").trim().take(MAX_NAME)
    val device = base.substringBefore('.').trimEnd().uppercase() in RESERVED
    return (if (device) "_$base" else base) + ".sng"
}

private const val MAX_NAME = 120
private val INVALID = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
private val RESERVED = setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }
