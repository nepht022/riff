package com.rodrigomachado.riff.feature.gameplay

import com.rodrigomachado.riff.core.audio.MusicStartFinder
import com.rodrigomachado.riff.core.data.MAX_SONG_OFFSET_MS
import com.rodrigomachado.riff.core.data.TrackMatchRepository
import com.rodrigomachado.riff.core.data.game.ChartAudio
import kotlin.math.abs

/**
 * Ajuste de sincronia de uma faixa do Spotify. Sem ajuste escolhido (um 0 escolhido vale), alinha pelo começo
 * da música no áudio do mapa ([initialSongOffset]). O valor fica salvo para o jogador refinar na pausa.
 */
internal class SpotifySongOffset(
    private val trackMatches: TrackMatchRepository,
    private val musicStarts: MusicStartFinder,
) {
    suspend fun forTrack(
        uri: String,
        chartId: String,
        fromRouteMs: Int,
        audio: ChartAudio?,
        spotifyDurationMs: Long?,
        firstNoteMs: Long?,
    ): Int {
        trackMatches.offsetFor(uri, chartId)?.let { return it }
        if (audio == null) return fromRouteMs
        val uris = when (audio) {
            is ChartAudio.Single -> listOf(audio.uri)
            is ChartAudio.Stems -> listOf(audio.guitarUri) + audio.backingUris
        }
        // Salvo mesmo quando dá 0 ou nada: a análise roda uma vez só, e o jogador refina daí.
        val info = musicStarts.analyze(uris)
        val start = initialSongOffset(info.musicStartMs, info.durationMs, spotifyDurationMs, firstNoteMs)
            .coerceIn(0, MAX_SONG_OFFSET_MS)
        trackMatches.saveOffset(uri, chartId, start)
        return start
    }
}

/**
 * O primeiro ajuste: quanto o áudio do mapa tem a mais no começo que a versão do Spotify. O começo detectado
 * ([startMs], primeiro som sustentado) supõe que o Spotify começa direto na música; numa mesma gravação com
 * introdução baixa (mapas oficiais: plateia, microfonia) os dois têm a introdução. Por isso o ajuste não passa
 * da sobra do áudio do mapa ([chartAudioMs] − [spotifyMs]; até [ENCODER_PADDING_MS], para mais ou para menos, é
 * a mesma gravação) nem da primeira nota (a música não começa depois dela). Sem as durações, ou com o áudio do mapa
 * mais curto (a diferença está no fim), vale o começo detectado.
 */
internal fun initialSongOffset(startMs: Long?, chartAudioMs: Long?, spotifyMs: Long?, firstNoteMs: Long?): Int {
    var offset = startMs ?: return 0
    if (chartAudioMs != null && spotifyMs != null && spotifyMs > 0) {
        val extra = chartAudioMs - spotifyMs
        offset = when {
            // A mesma gravação: até o enchimento do codificador, para mais ou para menos.
            abs(extra) <= ENCODER_PADDING_MS -> 0

            // O mapa tem áudio a mais: a introdução extra não passa disso.
            extra > 0 -> minOf(offset, extra)

            // O mapa acaba antes (outra mixagem, fade cortado): a diferença está no fim; vale a detecção.
            else -> offset
        }
    }
    if (firstNoteMs != null) offset = minOf(offset, firstNoteMs)
    return offset.coerceAtLeast(0).toInt()
}

/** Diferença de duração que é só o enchimento do codificador (e o arredondamento do Spotify), não introdução. */
private const val ENCODER_PADDING_MS = 300L
