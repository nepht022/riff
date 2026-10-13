package com.rodrigomachado.riff.core.data.game

import com.rodrigomachado.riff.core.data.AiSeparationRepository
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
    private val separations: AiSeparationRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : ChartTrackLoader {
    override suspend fun load(chartId: String, difficulty: Difficulty): PlayableChart = withContext(dispatcher) {
        val chart = charts.getChart(chartId) ?: throw ChartNotFoundException("mapa $chartId não está na biblioteca")
        val children = source.listChildren(chart.folderUri)
        val loaded = ChartFolderReader.read(source, children, FolderChartImporter.MAX_FILE_BYTES)
        val notes = loaded.tracks[difficulty] ?: throw ChartNotFoundException("mapa $chartId sem $difficulty")
        val audio = ChartAudioFinder.find(children.filter { !it.isDirectory })
        PlayableChart(chart, difficulty, notes, withAiSeparation(chartId, audio))
    }

    /** Sem guitarra separada na pasta, usa a da IA (se houver), com o áudio original de reserva. */
    private suspend fun withAiSeparation(chartId: String, audio: ChartAudio?): ChartAudio? {
        if (audio is ChartAudio.Stems) return audio
        val files = separations.separatedFiles(chartId) ?: return audio
        val guitar = "file://" + files.guitar.invariantSeparatorsPath
        return ChartAudio.Stems(
            guitar,
            listOf("file://" + files.backing.invariantSeparatorsPath),
            fallbackUri = audio?.singleUri ?: guitar,
        )
    }
}
