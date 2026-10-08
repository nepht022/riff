package com.rodrigomachado.riff.core.data.game

import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.IoDispatcher
import com.rodrigomachado.riff.core.data.folder.FolderSource
import com.rodrigomachado.riff.core.data.importer.ChartFolderReader
import com.rodrigomachado.riff.core.data.importer.FolderChartImporter
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Mapa pronto para jogar: notas da dificuldade escolhida e o áudio da pasta, se houver. */
data class PlayableChart(val chart: Chart, val difficulty: Difficulty, val notes: List<Note>, val audioUri: String?)

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
        val audio = AUDIO_NAMES.firstNotNullOfOrNull { name ->
            children.firstOrNull { !it.isDirectory && it.name.equals(name, ignoreCase = true) }
        }
        PlayableChart(chart, difficulty, notes, audio?.uri)
    }

    private companion object {
        /** Música completa primeiro; a faixa da guitarra serve se não houver outra. */
        val AUDIO_NAMES = listOf(
            "song.ogg",
            "song.opus",
            "song.mp3",
            "song.wav",
            "guitar.ogg",
            "guitar.opus",
            "guitar.mp3",
        )
    }
}
