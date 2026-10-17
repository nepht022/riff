package com.rodrigomachado.riff.feature.nowplaying

import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.NowPlaying

/**
 * Trocar de música na pausa de uma partida do Spotify: a partida deixa aqui o pedido (a dificuldade e a música de
 * onde saiu) e a tela espera o Spotify trocar. Com "mapa encontrado", a partida começa direto; sem a dificuldade no
 * mapa novo, pede a escolha; com sugestão ou sem mapa, fica nesta tela.
 */
object NowPlayingAutoPlay {
    /** Quanto esperar o Spotify trocar de música (sem conexão, por exemplo, não troca). */
    const val WAIT_MS = 6_000L

    internal const val KEY_DIFFICULTY = "autoPlayDifficulty"
    internal const val KEY_FROM = "autoPlayFromTrack"

    /** Chamado pela navegação, no estado salvo do Tocando agora, antes de voltar para ele. */
    fun request(saved: SavedStateHandle, difficulty: Difficulty, fromTrackUri: String) {
        saved[KEY_FROM] = fromTrackUri
        saved[KEY_DIFFICULTY] = difficulty.name
    }

    sealed interface Action {
        data class Start(val request: PlayRequest) : Action

        data class ChooseDifficulty(val trackUri: String) : Action
    }

    internal fun decide(nowPlaying: NowPlaying, difficulty: Difficulty): Action? {
        val match = nowPlaying.match as? MatchStatus.Matched ?: return null
        val uri = nowPlaying.playback.trackUri
        return if (difficulty in match.chart.difficulties) {
            Action.Start(PlayRequest(match.chart.id, difficulty, uri, match.trackOffsetMs))
        } else {
            Action.ChooseDifficulty(uri)
        }
    }
}
