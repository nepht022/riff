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
    var appVolume = 1f
    var preparedUri: String? = null
    var prepareFailure: Exception? = null
    var released = false
    override var isCompleted: Boolean = false

    // Como o MediaPlayer de verdade: usar depois de liberado é um erro.
    override var positionMs: Long = 0
        get() = field.also { checkNotReleased() }

    override var isPlaying: Boolean = false
        get() = field.also { checkNotReleased() }

    private fun checkNotReleased() = check(!released) { "player usado depois de liberado" }

    override suspend fun prepare(uri: String) {
        prepareFailure?.let { throw it }
        preparedUri = uri
    }

    override fun play() {
        checkNotReleased()
        isPlaying = true
    }

    override fun pause() {
        checkNotReleased()
        isPlaying = false
    }

    override fun setVolume(volume: Float) {
        appVolume = volume
    }

    override fun release() {
        released = true
    }
}

class FakeSfxPlayer : SfxPlayer {
    val hitLanes = mutableListOf<Int>()
    val hits: Int get() = hitLanes.size
    var misses = 0
    var hitVolume = -1f
    var missVolume = -1f
    var released = false

    override fun hit(lane: Int) {
        hitLanes += lane
    }

    override fun miss() {
        misses++
    }

    override fun setVolumes(hit: Float, miss: Float) {
        hitVolume = hit
        missVolume = miss
    }

    override fun release() {
        released = true
    }
}

class FakeChartTrackLoader : com.rodrigomachado.riff.core.data.game.ChartTrackLoader {
    var result: () -> com.rodrigomachado.riff.core.data.game.PlayableChart = { error("defina o resultado") }
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun load(
        chartId: String,
        difficulty: com.rodrigomachado.riff.core.model.Difficulty,
    ): com.rodrigomachado.riff.core.data.game.PlayableChart {
        gate?.await()
        return result()
    }
}

class FakeSettingsRepository(settings: com.rodrigomachado.riff.core.model.Settings) :
    com.rodrigomachado.riff.core.data.SettingsRepository {
    override val settings = kotlinx.coroutines.flow.MutableStateFlow(settings)

    override suspend fun update(
        transform: (com.rodrigomachado.riff.core.model.Settings) -> com.rodrigomachado.riff.core.model.Settings,
    ) {
        settings.value = transform(settings.value)
    }
}

class FakeScoreRepository : com.rodrigomachado.riff.core.data.ScoreRepository {
    val saved = mutableListOf<com.rodrigomachado.riff.core.model.ScoreRecord>()
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun save(record: com.rodrigomachado.riff.core.model.ScoreRecord): Long {
        gate?.await()
        saved += record
        return saved.size.toLong()
    }

    override suspend fun get(id: Long) = saved.getOrNull(id.toInt() - 1)?.copy(id = id)

    override suspend fun best(chartId: String, difficulty: com.rodrigomachado.riff.core.model.Difficulty) = null
}

class FakeStemPlayer(val song: FakeSongPlayer = FakeSongPlayer()) :
    com.rodrigomachado.riff.core.audio.StemPlayer,
    SongPlayer by song {
    val audible = mutableListOf<Boolean>()

    override fun setGuitarAudible(audible: Boolean) {
        this.audible += audible
    }
}
