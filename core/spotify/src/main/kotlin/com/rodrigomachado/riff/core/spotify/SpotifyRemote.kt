package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyConnection
import kotlinx.coroutines.flow.StateFlow

/** O app do Spotify, visto pelo Riff. Comandos são assíncronos; o efeito chega em [playback]. */
interface SpotifyRemote {
    val connection: StateFlow<SpotifyConnection>

    /** Último estado do player; `null` sem conexão ou sem uma faixa de música. */
    val playback: StateFlow<PlaybackState?>

    /** Conecta. Sem efeito se já estiver conectado ou conectando. */
    fun connect()

    /** Relê o estado agora (spec §4: releitura a cada 2 s). */
    suspend fun refresh(): PlaybackState?

    /** Toca [trackUri] do começo. */
    fun play(trackUri: String)

    fun pause()

    fun resume()
}
