package com.rodrigomachado.riff.core.data.game

import com.rodrigomachado.riff.core.chart.ChartAudioFiles
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.IoDispatcher
import com.rodrigomachado.riff.core.data.folder.FolderEntry
import com.rodrigomachado.riff.core.data.folder.FolderSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** O áudio da pasta de um mapa (sem a separação da IA), sem ler as notas. */
interface ChartAudioProbe {
    suspend fun audioOf(chartId: String): ChartAudio?
}

class FolderChartAudioProbe @Inject constructor(
    private val source: FolderSource,
    private val charts: ChartRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : ChartAudioProbe {
    override suspend fun audioOf(chartId: String): ChartAudio? = withContext(dispatcher) {
        val chart = charts.getChart(chartId) ?: return@withContext null
        ChartAudioFinder.find(source.listChildren(chart.folderUri).filter { !it.isDirectory })
    }
}

/** Um arquivo só, ou a guitarra separada do resto da banda. */
object ChartAudioFinder {
    fun find(files: List<FolderEntry>): ChartAudio? {
        val names = files.map { it.name }
        fun find(name: String) = ChartAudioFiles.find(names, name)?.let { found -> files.first { it.name == found } }
        val song = find("song")
        val guitar = find("guitar")
        val backing = ChartAudioFiles.BACKING_NAMES.mapNotNull(::find)
        return when {
            guitar != null && backing.isNotEmpty() ->
                ChartAudio.Stems(guitar.uri, backing.map { it.uri }, fallbackUri = song?.uri ?: guitar.uri)

            song != null -> ChartAudio.Single(song.uri)

            guitar != null -> ChartAudio.Single(guitar.uri)

            else -> null
        }
    }
}
