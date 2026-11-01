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
    override var isStarting: Boolean = false
    val seeks = mutableListOf<Long>()

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

    override fun seekTo(positionMs: Long) {
        seeks += positionMs
        this.positionMs = positionMs
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

class FakeChartOffsetRepository : com.rodrigomachado.riff.core.data.ChartOffsetRepository {
    val offsets = mutableMapOf<String, Int>()

    var failure: Exception? = null

    override suspend fun get(chartId: String): Int = failure?.let { throw it } ?: offsets[chartId] ?: 0

    override suspend fun save(chartId: String, offsetMs: Int) {
        offsets[chartId] = offsetMs
    }
}

class FakePlaybackInterruptions : com.rodrigomachado.riff.core.audio.PlaybackInterruptions {
    var starts = 0
    var stopped = false
    private var onInterrupted: (() -> Unit)? = null
    private var onBusy: (() -> Unit)? = null

    override fun start(onInterrupted: () -> Unit, onBusy: () -> Unit) {
        starts++
        this.onInterrupted = onInterrupted
        this.onBusy = onBusy
    }

    override fun stop() {
        stopped = true
        onInterrupted = null
        onBusy = null
    }

    /** Uma ligação, outro app tocando ou o fone desconectado. */
    fun interrupt() = onInterrupted?.invoke()

    /** O Android negou o foco de áudio (no meio de uma ligação). */
    fun deny() = onBusy?.invoke()
}

/** Imagem de mentira: o ViewModel só repassa a imagem do mapa para a tela. */
class FakeImage : androidx.compose.ui.graphics.ImageBitmap {
    override val width = 1
    override val height = 1
    override val config = androidx.compose.ui.graphics.ImageBitmapConfig.Argb8888
    override val hasAlpha = false
    override val colorSpace = androidx.compose.ui.graphics.colorspace.ColorSpaces.Srgb

    override fun prepareToDraw() = Unit

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int,
    ) = Unit
}

/** Ranking falso: guarda as partidas enviadas e devolve o status que o teste definir. */
class RecordingRanking(override val isConfigured: Boolean = true) :
    com.rodrigomachado.riff.core.data.ranking.RankingRepository {
    val submitted = mutableListOf<Pair<com.rodrigomachado.riff.core.model.ScoreRecord, String>>()
    val status = kotlinx.coroutines.flow.MutableStateFlow<com.rodrigomachado.riff.core.data.ranking.SubmitStatus?>(null)
    override val player = kotlinx.coroutines.flow.MutableStateFlow(
        com.rodrigomachado.riff.core.data.ranking.Player(null, null),
    )
    override val askedLater = kotlinx.coroutines.flow.MutableStateFlow(false)

    override fun askLater() = Unit

    override suspend fun register(name: String) = com.rodrigomachado.riff.core.data.ranking.NameResult.Ok

    override fun submitIfBest(record: com.rodrigomachado.riff.core.model.ScoreRecord, title: String, artist: String) {
        submitted += record to "$title — $artist"
    }

    override fun statusFor(scoreId: Long) = status

    override suspend fun flush() = Unit

    override suspend fun songs(query: String) = com.rodrigomachado.riff.core.data.ranking.ApiAnswer.Offline

    override suspend fun board(chartId: String) = com.rodrigomachado.riff.core.data.ranking.ApiAnswer.Offline
}
