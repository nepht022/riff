package com.rodrigomachado.riff.core.spotify.session

import kotlinx.coroutines.flow.StateFlow

/**
 * A sessão de mídia do Spotify vista pelo Riff ("Acesso às notificações"). Uma interface própria para os testes não
 * dependerem do `MediaController` do Android.
 */
interface SpotifySession {
    /** O Riff está ligado em "Acesso às notificações" (sem isso o Android não mostra as sessões). */
    val hasPermission: Boolean

    val isInstalled: Boolean

    /** O que o Spotify mostra agora; `null` sem sessão (Spotify fechado ou nada tocado ainda). */
    val snapshot: StateFlow<SessionSnapshot?>

    val queue: StateFlow<List<SessionQueueItem>>

    /** O item da fila que está tocando; `null` se o Spotify não disser. */
    val activeQueueId: StateFlow<Long?>

    /** Relê a permissão e as sessões (ao voltar ao app, ou quando o jogo pede). */
    fun recheck()

    fun play()

    fun pause()

    fun seekTo(ms: Long)

    fun skipToNext()

    fun skipToPrevious()
}
