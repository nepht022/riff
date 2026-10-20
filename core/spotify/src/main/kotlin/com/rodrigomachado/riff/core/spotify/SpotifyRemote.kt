package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** O app do Spotify, visto pelo Riff. Comandos são assíncronos; o efeito chega em [playback]. */
interface SpotifyRemote {
    val connection: StateFlow<SpotifyConnection>

    /** Último estado do player; `null` sem conexão ou sem uma faixa de música. */
    val playback: StateFlow<PlaybackState?>

    /** Anúncio tocando (conta grátis): sem música, mas o Spotify volta sozinho depois. */
    val adPlaying: StateFlow<Boolean> get() = NO_AD

    /** Conecta. Sem efeito se já estiver conectado ou conectando. */
    fun connect()

    /** Relê o estado agora (spec §4: releitura a cada 2 s). */
    suspend fun refresh(): PlaybackState?

    /** Toca [trackUri] do começo. */
    fun play(trackUri: String)

    fun pause()

    fun resume()

    fun seekTo(positionMs: Long)

    /** Próxima faixa da fila do Spotify. */
    fun skipNext()

    /** Faixa anterior; como no Spotify, passados os primeiros segundos volta ao começo da atual. */
    fun skipPrevious()

    /** A partida saiu: o que ainda esperava (o ⏮ do "Jogar de novo") não manda mais nada. */
    fun cancelPendingCommands() = Unit
}

private val NO_AD: StateFlow<Boolean> = MutableStateFlow(false)
