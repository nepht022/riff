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

/** A imagem do mapa (fundo, senão capa), em bytes, para mostrar atrás da pista. */
interface ChartArtLoader {
    suspend fun load(chartId: String): ByteArray?
}

/** `background.*` antes de `album.*`; só jpg, jpeg e png, sem diferenciar maiúsculas. */
object ChartArtFinder {
    private val EXTENSIONS = setOf("jpg", "jpeg", "png")

    fun find(files: List<FolderEntry>): FolderEntry? = named(files, "background") ?: named(files, "album")

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
    override suspend fun load(chartId: String): ByteArray? = withContext(io) {
        val chart = charts.getChart(chartId) ?: return@withContext null
        try {
            ChartArtFinder.find(source.listChildren(chart.folderUri))?.let { source.read(it, MAX_BYTES) }
        } catch (e: IOException) {
            null
        } catch (e: FolderAccessLostException) {
            null
        }
    }

    private companion object {
        const val MAX_BYTES = 8L * 1024 * 1024
    }
}
