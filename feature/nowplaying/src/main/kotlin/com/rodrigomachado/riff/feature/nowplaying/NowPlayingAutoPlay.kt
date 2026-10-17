package com.rodrigomachado.riff.feature.nowplaying

import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.NowPlaying

/**
 * Trocar de música na pausa, ou o fim de uma partida do Spotify: a partida deixa aqui o pedido (a dificuldade, a
 * música de onde saiu e o prazo) e a tela espera o Spotify trocar. Com "mapa encontrado", a partida começa direto;
 * sem a dificuldade no mapa novo, pede a escolha; com sugestão, a partida abre pedindo confirmação; sem mapa, fica.
 */
object NowPlayingAutoPlay {
    /** Quanto esperar o Spotify trocar de música (sem conexão, por exemplo, não troca). */
    const val WAIT_MS = 6_000L

    internal const val KEY_DIFFICULTY = "autoPlayDifficulty"
    internal const val KEY_FROM = "autoPlayFromTrack"
    internal const val KEY_WAIT = "autoPlayWaitMs"

    /**
     * Chamado pela navegação, no estado salvo do Tocando agora, antes de voltar para ele. [waitMs]: quanto esperar o
     * Spotify trocar (no fim da partida, o que falta da música; na troca na pausa, [WAIT_MS]).
     */
    fun request(saved: SavedStateHandle, difficulty: Difficulty, fromTrackUri: String, waitMs: Long = WAIT_MS) {
        saved[KEY_FROM] = fromTrackUri
        saved[KEY_WAIT] = waitMs
        saved[KEY_DIFFICULTY] = difficulty.name
    }

    /** Vale só enquanto [trackUri] for a música tocando. */
    sealed interface Action {
        val trackUri: String

        data class Start(val request: PlayRequest) : Action {
            override val trackUri: String get() = request.trackUri
        }

        data class ChooseDifficulty(override val trackUri: String) : Action

        /** Só sugestão: a partida abre pausada pedindo para confirmar o mapa (spec Spotify contínuo §2.3). */
        data class Confirm(val request: PlayRequest) : Action {
            override val trackUri: String get() = request.trackUri
        }
    }

    internal fun decide(nowPlaying: NowPlaying, difficulty: Difficulty): Action? {
        val uri = nowPlaying.playback.trackUri
        return when (val match = nowPlaying.match) {
            is MatchStatus.Matched -> if (difficulty in match.chart.difficulties) {
                Action.Start(PlayRequest(match.chart.id, difficulty, uri, match.trackOffsetMs))
            } else {
                Action.ChooseDifficulty(uri)
            }

            // Sem a dificuldade, confirmar e escolher seriam dois passos: o Tocando agora já tem os dois.
            is MatchStatus.Suggested -> if (difficulty in match.chart.difficulties) {
                Action.Confirm(PlayRequest(match.chart.id, difficulty, uri, 0, confirmFirst = true))
            } else {
                null
            }

            MatchStatus.NoMatch -> null
        }
    }
}
