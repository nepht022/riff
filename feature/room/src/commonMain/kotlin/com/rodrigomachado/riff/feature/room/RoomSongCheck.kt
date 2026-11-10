package com.rodrigomachado.riff.feature.room

import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.EncoreDownloads
import com.rodrigomachado.riff.core.data.download.ChartDownloader
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.room.EncoreRef
import com.rodrigomachado.riff.core.room.RoomSong

/** Este aparelho tem o mapa [chartId] (o SHA-1 das notas, o mesmo em todos os aparelhos) na biblioteca? */
fun interface ChartLookup {
    suspend fun has(chartId: String): Boolean
}

/** A música da biblioteca como vai para a sala (null = o mapa não está mais na biblioteca). */
fun interface RoomLibrary {
    suspend fun song(chartId: String): RoomSong?
}

/**
 * O que a sala usa da biblioteca deste aparelho: conferir se tem a música da sala ([has]), montar a música que o dono
 * escolheu ([songOf]) e baixar do Encore a que falta ([download]). Sem [downloader], não baixa.
 */
class RoomSongCheck(
    private val lookup: ChartLookup,
    private val library: RoomLibrary,
    private val downloader: ChartDownloader?,
) {
    /** Dá para baixar [song] daqui: ela veio do Encore e este aparelho baixa. */
    fun canDownload(song: RoomSong): Boolean = downloader != null && song.encore != null

    suspend fun has(chartId: String): Boolean = lookup.has(chartId)

    suspend fun songOf(chartId: String): RoomSong? = library.song(chartId)

    /**
     * Baixa o pacote do Encore de [song] e confere de novo pelo `chartId` (o mesmo pacote dá o mesmo SHA-1): true =
     * agora tem. Sem o Encore na música ou sem downloader, false.
     *
     * @throws com.rodrigomachado.riff.core.data.download.ChartDownloadException se o download falhou.
     */
    suspend fun download(song: RoomSong, onProgress: (Float) -> Unit): Boolean {
        val chart = song.toEncoreChart() ?: return false
        val downloader = downloader ?: return false
        downloader.download(chart, onProgress)
        return lookup.has(song.chartId)
    }

    companion object {
        /** Sem biblioteca: não tem nenhuma música e não baixa (o padrão dos testes da sala). */
        val NONE = RoomSongCheck(lookup = { false }, library = { null }, downloader = null)

        /**
         * A biblioteca de verdade: o repositório de mapas e o md5 do Encore guardado no download (`encore_charts`).
         * O vídeo de fundo não fica guardado: a sala leva `video: false` (o download vem com o vídeo, quando o
         * pacote tem um; as notas, e então o `chartId`, são as mesmas).
         */
        fun of(charts: ChartRepository, downloads: EncoreDownloads, downloader: ChartDownloader) = RoomSongCheck(
            lookup = { charts.getChart(it) != null },
            library = { id ->
                charts.getChart(id)?.let { chart ->
                    RoomSong(
                        chartId = chart.id,
                        title = chart.title.take(MAX_TEXT),
                        artist = chart.artist.take(MAX_TEXT),
                        difficulties = chart.difficulties,
                        durationMs = chart.durationMs,
                        encore = downloads.md5Of(id)?.let { EncoreRef(it, video = false, charter = chart.charter) },
                    )
                }
            },
            downloader = downloader,
        )

        /** O limite das regras para `titulo` e `artista`. */
        private const val MAX_TEXT = 200
    }
}

/** O pacote do Encore da música da sala, para o [ChartDownloader] (null = não veio do Encore). */
internal fun RoomSong.toEncoreChart(): EncoreChart? = encore?.let { ref ->
    EncoreChart(
        md5 = ref.md5,
        name = title,
        artist = artist,
        charter = ref.charter,
        durationMs = durationMs,
        guitarDifficulties = difficulties,
        hasVideoBackground = ref.video,
    )
}
