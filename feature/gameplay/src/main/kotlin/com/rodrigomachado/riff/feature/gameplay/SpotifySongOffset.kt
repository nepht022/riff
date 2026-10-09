package com.rodrigomachado.riff.feature.gameplay

import com.rodrigomachado.riff.core.audio.MusicStartFinder
import com.rodrigomachado.riff.core.data.MAX_SONG_OFFSET_MS
import com.rodrigomachado.riff.core.data.TrackMatchRepository
import com.rodrigomachado.riff.core.data.game.ChartAudio

/**
 * Ajuste de sincronia de uma faixa do Spotify. Sem ajuste escolhido (um 0 escolhido vale), alinha pelo começo
 * da música no áudio do mapa: a versão do Spotify não tem a contagem de entrada nem o silêncio que o áudio do
 * mapa pode ter. O valor fica salvo para o jogador refinar na pausa.
 */
internal class SpotifySongOffset(
    private val trackMatches: TrackMatchRepository,
    private val musicStarts: MusicStartFinder,
) {
    suspend fun forTrack(uri: String, chartId: String, fromRouteMs: Int, audio: ChartAudio?): Int {
        trackMatches.offsetFor(uri, chartId)?.let { return it }
        if (audio == null) return fromRouteMs
        val uris = when (audio) {
            is ChartAudio.Single -> listOf(audio.uri)
            is ChartAudio.Stems -> listOf(audio.guitarUri) + audio.backingUris
        }
        // Salvo mesmo quando dá 0 ou nada: a análise roda uma vez só, e o jogador refina daí.
        val start = musicStarts.musicStartMs(uris)?.toInt()?.coerceIn(0, MAX_SONG_OFFSET_MS) ?: 0
        trackMatches.saveOffset(uri, chartId, start)
        return start
    }
}
