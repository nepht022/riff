package com.rodrigomachado.riff.core.data.game

import com.rodrigomachado.riff.core.chart.ChartAudioFiles
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.IoDispatcher
import com.rodrigomachado.riff.core.data.folder.FolderEntry
import com.rodrigomachado.riff.core.data.folder.FolderSource
import com.rodrigomachado.riff.core.data.importer.ChartFolderReader
import com.rodrigomachado.riff.core.data.importer.FolderChartImporter
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Áudio da pasta do mapa: um arquivo só, ou a guitarra separada do resto da banda. */
sealed interface ChartAudio {
    /** O que tocar sem as faixas separadas: song.* (ou a guitarra, se for a única). */
    val singleUri: String

    data class Single(val uri: String) : ChartAudio {
        override val singleUri: String get() = uri
    }

    data class Stems(val guitarUri: String, val backingUris: List<String>, val fallbackUri: String) : ChartAudio {
        override val singleUri: String get() = fallbackUri
    }
}

/** Mapa pronto para jogar: notas da dificuldade escolhida e o áudio da pasta, se houver. */
data class PlayableChart(val chart: Chart, val difficulty: Difficulty, val notes: List<Note>, val audio: ChartAudio?)

class ChartNotFoundException(message: String) : Exception(message)

interface ChartTrackLoader {
    /**
     * @throws ChartNotFoundException se o mapa não estiver na biblioteca ou não tiver a dificuldade.
     * @throws com.rodrigomachado.riff.core.data.folder.FolderAccessLostException se a pasta não puder ser lida.
     */
    suspend fun load(chartId: String, difficulty: Difficulty): PlayableChart
}

class FolderChartTrackLoader @Inject constructor(
    private val source: FolderSource,
    private val charts: ChartRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : ChartTrackLoader {
    override suspend fun load(chartId: String, difficulty: Difficulty): PlayableChart = withContext(dispatcher) {
        val chart = charts.getChart(chartId) ?: throw ChartNotFoundException("mapa $chartId não está na biblioteca")
        val children = source.listChildren(chart.folderUri)
        val loaded = ChartFolderReader.read(source, children, FolderChartImporter.MAX_FILE_BYTES)
        val notes = loaded.tracks[difficulty] ?: throw ChartNotFoundException("mapa $chartId sem $difficulty")
        PlayableChart(chart, difficulty, notes, audioOf(children.filter { !it.isDirectory }))
    }

    private fun audioOf(files: List<FolderEntry>): ChartAudio? {
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
