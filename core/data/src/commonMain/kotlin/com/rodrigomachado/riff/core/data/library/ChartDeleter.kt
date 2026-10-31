package com.rodrigomachado.riff.core.data.library

import com.rodrigomachado.riff.core.chart.sng.SngRef
import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.IoDispatcher
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.folder.FolderWriter
import com.rodrigomachado.riff.core.model.Chart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Quantos mapas pedidos saíram do celular e quantos não deu para apagar. */
data class DeletionResult(val deleted: Int, val failed: Int)

/** Apaga mapas do celular (a pasta, ou o `.sng`) e da biblioteca. */
interface ChartDeleter {
    /** Quantos outros mapas estão dentro das pastas destes e vão junto. */
    suspend fun alsoDeleted(chartIds: Collection<String>): Int

    suspend fun delete(chartIds: Collection<String>): DeletionResult
}

/** URIs das pastas da biblioteca (SAF: `.../tree/<raiz>/document/<pasta>`; nos testes, caminhos com `/`). */
object LibraryFolders {
    /** A pasta é a própria raiz da biblioteca (um mapa solto nela não pode ser apagado com a pasta). */
    fun isRoot(folderUri: String, rootUri: String?): Boolean {
        if (rootUri == null) return false
        if (folderUri == rootUri) return true
        val document = folderUri.substringAfter("/document/", missingDelimiterValue = "")
        val root = rootUri.substringAfter("/tree/", missingDelimiterValue = "").substringBefore('/')
        return document.isNotEmpty() && document == root
    }

    /** [child] fica dentro da pasta [parent] (subpasta ou pacote `.sng` dentro dela). */
    fun isInside(child: String, parent: String): Boolean =
        child != parent && (child.startsWith("$parent/") || child.startsWith("$parent%2F"))
}

class FolderChartDeleter @Inject constructor(
    private val charts: ChartRepository,
    private val writer: FolderWriter,
    private val folders: LibraryFolderStore,
    private val separations: AiSeparationRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ChartDeleter {
    override suspend fun alsoDeleted(chartIds: Collection<String>): Int = withContext(io) {
        val all = charts.observeCharts().first()
        val chosen = all.filter { it.id in chartIds }
        all.count { other ->
            other.id !in chartIds && chosen.any {
                LibraryFolders.isInside(
                    other.folderUri,
                    it.folderUri,
                )
            }
        }
    }

    override suspend fun delete(chartIds: Collection<String>): DeletionResult = withContext(io) {
        val root = folders.rootUri.first()
        val all = charts.observeCharts().first()
        var deleted = 0
        var failed = 0
        for (chart in all.filter { it.id in chartIds }) {
            if (LibraryFolders.isRoot(chart.folderUri, root) || !writer.deleteDocument(targetOf(chart))) {
                failed++
                continue
            }
            charts.remove(
                listOf(chart.id) + all.filter { LibraryFolders.isInside(it.folderUri, chart.folderUri) }.map { it.id },
            )
            deleted++
        }
        if (deleted > 0) separations.pruneOrphans() // as separações por IA desses mapas
        DeletionResult(deleted, failed)
    }

    /** O `.sng` em si, ou a pasta do mapa. */
    private fun targetOf(chart: Chart) = SngRef.packageFileOf(chart.folderUri) ?: chart.folderUri
}
