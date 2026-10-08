package com.rodrigomachado.riff.feature.gameplay

import com.rodrigomachado.riff.core.audio.SfxPlayer
import com.rodrigomachado.riff.core.audio.SongPlayer
import com.rodrigomachado.riff.core.game.GameEngine
import com.rodrigomachado.riff.core.game.SongClock
import com.rodrigomachado.riff.core.game.TimeSource
import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.model.Note
import com.rodrigomachado.riff.core.ui.stage.FLAME_DURATION_MS
import kotlin.math.max

/**
 * Uma partida: junta o motor, o relógio da música, o player e os sons.
 * Não conhece a tela; a tela chama [tick] a cada frame e repassa os toques.
 */
class GameSession(
    notes: List<Note>,
    private val player: SongPlayer,
    private val sfx: SfxPlayer,
    private val timeSource: TimeSource,
    audioOffsetMs: Long,
) {
    val engine = GameEngine(notes)
    private val clock = SongClock(timeSource, audioOffsetMs)
    private val pressed = BooleanArray(Note.LANE_COUNT)
    private val flameStartedAt = LongArray(Note.LANE_COUNT) { NEVER }
    private val lastNoteEndMs = engine.notes.maxOfOrNull { it.timeMs + it.sustainMs } ?: 0L
    private var lastSyncAt = NEVER
    private var missesHeard = 0
    private var lastJudgmentAt = NEVER

    /** Quanto tempo atrás do relógio ainda desenhar (notas perdidas passando e rastros longos). */
    val lookBehindMs: Long = max(MISS_TAIL_MS, engine.notes.maxOfOrNull { it.sustainMs } ?: 0L)

    var songTimeMs: Long = 0
        private set

    var lastJudgment: Judgment? = null
        private set

    val isPaused: Boolean get() = !player.isPlaying

    val isFinished: Boolean
        get() = player.isCompleted || (engine.isFinished && songTimeMs > lastNoteEndMs + END_GRACE_MS)

    fun start() {
        player.play()
        syncClock()
    }

    fun pause() {
        player.pause()
        syncClock()
        for (lane in pressed.indices) release(lane)
    }

    fun resume() {
        player.play()
        syncClock()
    }

    /** Avança a partida até o instante atual. Chamado a cada frame. */
    fun tick(): Long {
        if (timeSource.nowMs() - lastSyncAt >= SYNC_INTERVAL_MS) syncClock()
        songTimeMs = clock.positionMs()
        engine.update(songTimeMs)
        val misses = engine.count(Judgment.MISS)
        if (misses > missesHeard) {
            missesHeard = misses
            sfx.miss()
        }
        return songTimeMs
    }

    fun press(lane: Int) {
        if (isPaused) return
        pressed[lane] = true
        val judgment = engine.onPress(lane, clock.positionMs())
        if (judgment == null) {
            sfx.miss()
            return
        }
        sfx.hit(lane)
        val now = timeSource.nowMs()
        flameStartedAt[lane] = now
        lastJudgment = judgment
        lastJudgmentAt = now
    }

    fun release(lane: Int) {
        if (!pressed[lane]) return
        pressed[lane] = false
        engine.onRelease(lane, clock.positionMs())
    }

    fun isPressed(lane: Int): Boolean = pressed[lane]

    /** Progresso da chama da [lane] de 0 a 1 (acima de 1 já apagou); negativo se nunca acendeu. */
    fun flameProgress(lane: Int): Float {
        val start = flameStartedAt[lane]
        if (start == NEVER) return -1f
        return (timeSource.nowMs() - start).toFloat() / FLAME_DURATION_MS
    }

    /** Há quanto tempo apareceu o último julgamento. */
    fun judgmentAgeMs(): Long = if (lastJudgmentAt == NEVER) Long.MAX_VALUE else timeSource.nowMs() - lastJudgmentAt

    private fun syncClock() {
        val now = timeSource.nowMs()
        clock.sync(player.positionMs, !player.isPlaying, now)
        lastSyncAt = now
    }

    companion object {
        const val SYNC_INTERVAL_MS = 500L
        const val END_GRACE_MS = 3_000L
        private const val MISS_TAIL_MS = 300L
        private const val NEVER = Long.MIN_VALUE
    }
}
