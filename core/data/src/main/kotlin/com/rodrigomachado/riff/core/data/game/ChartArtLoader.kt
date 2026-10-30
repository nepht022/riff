package com.rodrigomachado.riff.core.data.game

import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.IoDispatcher
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.folder.FolderEntry
import com.rodrigomachado.riff.core.data.folder.FolderSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** A imagem do mapa (fundo, senão capa) para mostrar atrás da pista. */
interface ChartArtLoader {
    /** A primeira imagem que [decode] aceita: um fundo grande demais ou corrompido dá lugar à capa. */
    suspend fun <T : Any> load(chartId: String, decode: (ByteArray) -> T?): T?

    suspend fun load(chartId: String): ByteArray? = load(chartId) { it }
}

/** `background.*` antes de `album.*`; só jpg, jpeg e png, sem diferenciar maiúsculas. */
object ChartArtFinder {
    private val EXTENSIONS = setOf("jpg", "jpeg", "png")

    fun find(files: List<FolderEntry>): FolderEntry? = candidates(files).firstOrNull()

    fun candidates(files: List<FolderEntry>): List<FolderEntry> = listOfNotNull(
        named(files, "background"),
        named(files, "album"),
    )

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
    // Sem imagem (grande demais, ilegível, pasta perdida), a pista fica só com o fundo escolhido.
    @Suppress("SwallowedException")
    override suspend fun <T : Any> load(chartId: String, decode: (ByteArray) -> T?): T? = withContext(io) {
        val chart = charts.getChart(chartId) ?: return@withContext null
        val candidates = try {
            ChartArtFinder.candidates(source.listChildren(chart.folderUri))
        } catch (e: FolderAccessLostException) {
            return@withContext null
        }
        candidates.firstNotNullOfOrNull { entry ->
            try {
                decode(source.read(entry, MAX_BYTES))
            } catch (e: IOException) {
                null
            } catch (e: FolderAccessLostException) {
                null
            }
        }
    }

    private companion object {
        const val MAX_BYTES = 8L * 1024 * 1024
    }
}
