package com.rodrigomachado.riff.core.data.game

import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.IoDispatcher
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.folder.FolderEntry
import com.rodrigomachado.riff.core.data.folder.FolderSource
import com.rodrigomachado.riff.core.model.ChartArtUnavailableException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** Para que é a imagem: o fundo da pista (o `background.*` primeiro) ou a miniatura das listas (o `album.*`). */
enum class ChartArtUse { TRACK, THUMB }

/** A imagem do mapa: atrás da pista (fundo, senão capa) ou nas listas (capa, senão fundo). */
interface ChartArtLoader {
    /**
     * A primeira imagem que [decode] aceita, na ordem de [use]: uma grande demais ou corrompida dá lugar à outra; null
     * quando o mapa não tem imagem que sirva.
     *
     * @throws ChartArtUnavailableException se não deu para procurar (mapa fora da biblioteca, pasta sem acesso).
     */
    suspend fun <T : Any> load(
        chartId: String,
        use: ChartArtUse = ChartArtUse.TRACK,
        decode: suspend (ByteArray) -> T?,
    ): T?

    suspend fun load(chartId: String): ByteArray? = load(chartId) { it }
}

/**
 * Para a pista, `background.*` antes de `album.*`; para a miniatura, a capa antes (quadrada e menor: lê e decodifica
 * mais rápido). Só jpg, jpeg e png, sem diferenciar maiúsculas.
 */
object ChartArtFinder {
    private val EXTENSIONS = setOf("jpg", "jpeg", "png")

    fun find(files: List<FolderEntry>): FolderEntry? = candidates(files).firstOrNull()

    fun candidates(files: List<FolderEntry>, use: ChartArtUse = ChartArtUse.TRACK): List<FolderEntry> {
        val background = named(files, "background")
        val album = named(files, "album")
        return when (use) {
            ChartArtUse.TRACK -> listOfNotNull(background, album)
            ChartArtUse.THUMB -> listOfNotNull(album, background)
        }
    }

    private fun named(files: List<FolderEntry>, base: String) = files.firstOrNull {
        !it.isDirectory &&
            it.name.substringBeforeLast('.').equals(base, ignoreCase = true) &&
            it.name.substringAfterLast('.', "").lowercase() in EXTENSIONS
    }
}

class FolderChartArtLoader @Inject constructor(
    private val source: FolderSource,
    private val charts: ChartRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ChartArtLoader {
    // Sem imagem (grande demais, ilegível), a pista fica só com o fundo escolhido. Sem o mapa ou sem a pasta, não
    // deu para procurar: a exceção própria, que as capas não guardam (o mapa pode chegar, a pasta pode voltar).
    @Suppress("SwallowedException")
    override suspend fun <T : Any> load(chartId: String, use: ChartArtUse, decode: suspend (ByteArray) -> T?): T? =
        withContext(io) {
            val chart = charts.getChart(chartId)
                ?: throw ChartArtUnavailableException("mapa fora da biblioteca: $chartId")
            val candidates = try {
                ChartArtFinder.candidates(source.listChildren(chart.folderUri), use)
            } catch (e: IOException) {
                throw ChartArtUnavailableException("pasta ilegível", e)
            } catch (e: FolderAccessLostException) {
                throw ChartArtUnavailableException("pasta sem acesso", e)
            }
            candidates.firstNotNullOfOrNull { entry ->
                try {
                    decode(source.read(entry, MAX_BYTES))
                } catch (e: IOException) {
                    null
                } catch (e: FolderAccessLostException) {
                    throw ChartArtUnavailableException("pasta sem acesso", e)
                }
            }
        }

    private companion object {
        const val MAX_BYTES = 8L * 1024 * 1024
    }
}
