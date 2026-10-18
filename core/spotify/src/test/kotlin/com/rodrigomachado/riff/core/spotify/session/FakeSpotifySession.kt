package com.rodrigomachado.riff.core.spotify.session

import kotlinx.coroutines.flow.MutableStateFlow

/** A sessão do Spotify em memória: os testes mudam o estado e conferem os comandos. */
class FakeSpotifySession : SpotifySession {
    override var hasPermission = true
    override var isInstalled = true
    override val snapshot = MutableStateFlow<SessionSnapshot?>(null)
    override val queue = MutableStateFlow<List<SessionQueueItem>>(emptyList())
    override val activeQueueId = MutableStateFlow<Long?>(null)
    val commands = mutableListOf<String>()
    var rechecks = 0

    override fun recheck() {
        rechecks++
    }

    override fun play() {
        commands += "play"
    }

    override fun pause() {
        commands += "pause"
    }

    override fun seekTo(ms: Long) {
        commands += "seekTo:$ms"
    }

    override fun skipToNext() {
        commands += "next"
    }

    override fun skipToPrevious() {
        commands += "previous"
    }
}
