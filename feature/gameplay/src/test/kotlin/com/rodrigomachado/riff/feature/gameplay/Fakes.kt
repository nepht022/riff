package com.rodrigomachado.riff.feature.gameplay

import com.rodrigomachado.riff.core.audio.SfxPlayer
import com.rodrigomachado.riff.core.audio.SongPlayer
import com.rodrigomachado.riff.core.game.TimeSource
import com.rodrigomachado.riff.core.model.Note

fun note(timeMs: Long, lane: Int = 0, sustainMs: Long = 0) = Note(timeMs, lane, sustainMs, isHopo = false)

class FakeTime(var now: Long = 0) : TimeSource {
    override fun nowMs() = now
}

class FakeSongPlayer : SongPlayer {
    var preparedUri: String? = null
    var prepareFailure: Exception? = null
    var released = false
    override var positionMs: Long = 0
    override var isPlaying: Boolean = false
    override var isCompleted: Boolean = false

    override suspend fun prepare(uri: String) {
        prepareFailure?.let { throw it }
        preparedUri = uri
    }

    override fun play() {
        isPlaying = true
    }

    override fun pause() {
        isPlaying = false
    }

    override fun release() {
        released = true
    }
}

class FakeSfxPlayer : SfxPlayer {
    var hits = 0
    var misses = 0
    var lastVolume = -1f
    var released = false

    override fun hit() {
        hits++
    }

    override fun miss() {
        misses++
    }

    override fun setVolume(volume: Float) {
        lastVolume = volume
    }

    override fun release() {
        released = true
    }
}
