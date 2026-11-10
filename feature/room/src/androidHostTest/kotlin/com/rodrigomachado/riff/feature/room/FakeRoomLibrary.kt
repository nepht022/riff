package com.rodrigomachado.riff.feature.room

import com.rodrigomachado.riff.core.data.download.ChartDownloadException
import com.rodrigomachado.riff.core.data.download.ChartDownloader
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.room.EncoreRef
import com.rodrigomachado.riff.core.room.RoomSong
import kotlinx.coroutines.CompletableDeferred

/** Mapas de teste (o `chartId` é o SHA-1: 40 letras hexadecimais). */
internal const val CHART_A = "a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1"
internal const val CHART_B = "b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2"
internal const val MD5_A = "0123456789abcdef0123456789abcdef"

internal val SONG_A = RoomSong(
    chartId = CHART_A,
    title = "Through the Fire",
    artist = "DragonForce",
    difficulties = setOf(Difficulty.EASY, Difficulty.EXPERT),
    durationMs = 441_000,
    encore = EncoreRef(MD5_A, video = false, charter = "Harmonix"),
)

internal val SONG_B = SONG_A.copy(chartId = CHART_B, title = "Outra", encore = null)

/** A `musica` da sala em JSON, para o `seedRoom`. */
internal fun songJson(song: RoomSong): String {
    val encore = song.encore?.let { """, "encore": {"md5": "${it.md5}", "video": ${it.video}}""" }.orEmpty()
    return """{"chartId": "${song.chartId}", "titulo": "${song.title}", "artista": "${song.artist}",
        "dificuldades": "${song.difficulties.sorted().joinToString(",")}"$encore}"""
}

/**
 * A biblioteca de mentira de um aparelho: os mapas que ele tem ([owned]), a música que cada um vira na sala ([songs])
 * e o download do Encore, que espera o [gate] e então põe o mapa [gives] na biblioteca (ou falha com [failure]).
 */
internal class FakeRoomLibrary(vararg have: String) {
    val owned = have.toMutableSet()
    val songs = mutableMapOf(CHART_A to SONG_A, CHART_B to SONG_B)
    val downloads = mutableListOf<EncoreChart>()
    var gate: CompletableDeferred<Unit>? = null
    var gives: String? = CHART_A
    var failure: ChartDownloadException.Reason? = null

    private val downloader = object : ChartDownloader {
        override suspend fun download(chart: EncoreChart, onProgress: (Float) -> Unit): Chart {
            downloads += chart
            onProgress(HALF)
            gate?.await()
            failure?.let { throw ChartDownloadException(it, null) }
            val id = gives ?: "c3".repeat(20)
            owned += id
            return Chart(
                id, chart.name, chart.artist, null, null, null, "u", ChartFormat.CHART, setOf(Difficulty.EXPERT),
            )
        }
    }

    fun check() = RoomSongCheck(lookup = { it in owned }, library = { songs[it] }, downloader = downloader)

    private companion object {
        const val HALF = 0.5f
    }
}
