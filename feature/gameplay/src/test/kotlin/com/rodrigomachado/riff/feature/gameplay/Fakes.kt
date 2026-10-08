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

class FakeChartTrackLoader : com.rodrigomachado.riff.core.data.game.ChartTrackLoader {
    var result: () -> com.rodrigomachado.riff.core.data.game.PlayableChart = { error("defina o resultado") }

    override suspend fun load(chartId: String, difficulty: com.rodrigomachado.riff.core.model.Difficulty) = result()
}

class FakeSettingsRepository(settings: com.rodrigomachado.riff.core.model.Settings) :
    com.rodrigomachado.riff.core.data.SettingsRepository {
    override val settings = kotlinx.coroutines.flow.MutableStateFlow(settings)
}

class FakeScoreRepository : com.rodrigomachado.riff.core.data.ScoreRepository {
    val saved = mutableListOf<com.rodrigomachado.riff.core.model.ScoreRecord>()

    override suspend fun save(record: com.rodrigomachado.riff.core.model.ScoreRecord): Long {
        saved += record
        return saved.size.toLong()
    }

    override suspend fun get(id: Long) = saved.getOrNull(id.toInt() - 1)?.copy(id = id)

    override suspend fun best(chartId: String, difficulty: com.rodrigomachado.riff.core.model.Difficulty) = null
}
