package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyConnection
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSpotifyRemote : SpotifyRemote {
    var connectTo: SpotifyConnection = SpotifyConnection.Connected
    val commands = mutableListOf<String>()
    var refreshes = 0
    var connects = 0
    override val connection = MutableStateFlow<SpotifyConnection>(SpotifyConnection.Idle)
    override val playback = MutableStateFlow<PlaybackState?>(null)
    override val adPlaying = MutableStateFlow(false)

    override fun connect() {
        connects++
        connection.value = connectTo
    }

    override suspend fun refresh(): PlaybackState? {
        refreshes++
        return playback.value
    }

    override fun play(trackUri: String) {
        commands += "play:$trackUri"
    }

    override fun pause() {
        commands += "pause"
    }

    override fun resume() {
        commands += "resume"
    }

    override fun seekTo(positionMs: Long) {
        commands += "seek:$positionMs"
    }

    override fun skipNext() {
        commands += "next"
    }

    override fun skipPrevious() {
        commands += "previous"
    }

    var cancels = 0

    override fun cancelPendingCommands() {
        cancels++
    }
}
