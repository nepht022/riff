package com.rodrigomachado.riff.feature.gameplay

import com.rodrigomachado.riff.core.audio.GuitarTrackControl
import com.rodrigomachado.riff.core.audio.SfxPlayer
import com.rodrigomachado.riff.core.audio.SongPlayer
import com.rodrigomachado.riff.core.game.GameEngine
import com.rodrigomachado.riff.core.game.SongClock
import com.rodrigomachado.riff.core.game.TimeSource
import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.model.Note
import com.rodrigomachado.riff.core.ui.stage.FLAME_DURATION_MS
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Uma partida: junta o motor, o relógio da música, o player e os sons.
 * Não conhece a tela; a tela chama [tick] a cada frame e repassa os toques.
 */
@Suppress("TooManyFunctions") // a partida: relógio, toques, chamas e sons; dividir espalharia o estado
class GameSession(
    notes: List<Note>,
    private val player: SongPlayer,
    private val sfx: SfxPlayer,
    private val timeSource: TimeSource,
    audioOffsetMs: Long,
    /** Falso quando a música inteira já está tocando (sem guitarra separada): o acerto fica em silêncio. */
    private val hitSounds: Boolean = true,
    /** Quanto voltar ao continuar depois de uma pausa (modo biblioteca: dá tempo de se preparar). */
    private val rewindOnResumeMs: Long = 0,
    /** Tocar com palheta (PC); sem ela, apertar a cor na hora basta, como o toque (Configurações → Controles). */
    private val strum: Boolean = true,
) {
    val engine = GameEngine(notes)
    private val clock = SongClock(timeSource, audioOffsetMs)

    /** Dedos em cada trilha: a trilha só é solta quando o último sai. */
    private val fingers = IntArray(Note.LANE_COUNT)

    /** As cores seguradas no teclado, mesmo na pausa e na fita; [engagedFrets] são as que já valem na trilha. */
    private val heldFrets = sortedSetOf<Int>()
    private val engagedFrets = HashSet<Int>()
    private val pointerLanes = HashMap<Long, Int>()
    private val guitar = player as? GuitarTrackControl
    private val flameStartedAt = LongArray(Note.LANE_COUNT) { NEVER }
    private val lastNoteEndMs = engine.notes.maxOfOrNull { it.timeMs + it.sustainMs } ?: 0L
    private val endMs = lastNoteEndMs + END_GRACE_MS
    private var lastSyncAt = NEVER
    private var missesHeard = 0
    private var lastJudgmentAt = NEVER
    private var pendingOffsetMs = 0L
    private var waitingForSound = false
    private var silencedForOverdueNote = false

    /** A pista voltando como uma fita antes de a música continuar (modo biblioteca). */
    private class Rewind(val fromMs: Long, val toMs: Long, val startedAtMs: Long)

    private var rewind: Rewind? = null
    private var alreadyRewound = false

    /** Quanto tempo atrás do relógio ainda desenhar (notas perdidas passando e rastros longos). */
    val lookBehindMs: Long = max(MISS_TAIL_MS, engine.notes.maxOfOrNull { it.sustainMs } ?: 0L)

    var songTimeMs: Long = 0
        private set

    var lastJudgment: Judgment? = null
        private set

    val isPaused: Boolean get() = !player.isPlaying && rewind == null

    val isFinished: Boolean
        get() = player.isCompleted || (engine.isFinished && songTimeMs > endMs)

    /** Quanto da partida já passou, de 0 a 1: enche quando vem o resultado (nas duas origens de música). */
    val progress: Float get() = (songTimeMs.toFloat() / endMs).coerceIn(0f, 1f)

    fun start() {
        player.play()
        syncClock()
    }

    fun pause() {
        // Pausou no meio da fita: a música já está no ponto de retomada; continuar não volta mais 0,5 s.
        // Só liga, nunca desliga aqui: uma segunda pausa seguida (ligação e depois segundo plano) não pode apagar.
        if (rewind != null) alreadyRewound = true
        rewind = null
        player.pause()
        syncClock()
        pointerLanes.clear()
        // As cores seguradas no teclado continuam seguradas, mas soltam a trilha; voltam a valer quando a música anda.
        engagedFrets.clear()
        for (lane in fingers.indices) {
            if (fingers[lane] == 0) continue
            fingers[lane] = 1 // pausar solta a trilha de vez, com quantos dedos estiverem nela
            release(lane, lateByMs = 0)
        }
    }

    fun resume() {
        val shownMs = clock.positionMs()
        applyPendingOffset()
        val position = player.positionMs
        val target = if (alreadyRewound) position else (position - rewindOnResumeMs).coerceAtLeast(0)
        alreadyRewound = false
        if (target < position) {
            player.seekTo(target)
            rewind = Rewind(shownMs, target + clock.offsetMs, timeSource.nowMs())
            return
        }
        player.play()
        syncClock()
    }

    /** Ajuste fino de sincronia desta música. Pausado, a pista fica parada e o ajuste vale ao continuar. */
    fun adjustOffset(deltaMs: Long) {
        if (isPaused) {
            pendingOffsetMs += deltaMs
            return
        }
        clock.offsetMs += deltaMs
        songTimeMs = clock.positionMs()
    }

    /** Avança a partida até o instante atual. Chamado a cada frame. */
    fun tick(): Long {
        rewind?.let { return rewindStep(it) }
        engageHeldFrets()
        // A música voltou por fora do jogo: o ajuste feito na pausa vale agora.
        if (pendingOffsetMs != 0L && !isPaused) applyPendingOffset()
        // Começando (o som ainda não saiu), ou tocando/pausando por fora do jogo (o relógio discorda do player),
        // sincroniza neste quadro em vez de esperar a sincronização periódica.
        val mustSync = waitingForSound || player.isStarting || player.isPlaying == clock.isPaused
        if (mustSync || timeSource.nowMs() - lastSyncAt >= SYNC_INTERVAL_MS) syncClock()
        songTimeMs = clock.positionMs()
        engine.update(songTimeMs, MISS_GRACE_MS)
        val misses = engine.count(Judgment.MISS)
        if (misses > missesHeard) {
            missesHeard = misses
            onMiss()
        }
        silenceGuitarForOverdueNote()
        return songTimeMs
    }

    private fun applyPendingOffset() {
        clock.offsetMs += pendingOffsetMs
        pendingOffsetMs = 0
    }

    /** Um quadro da fita voltando; no fim, a música continua do ponto novo. */
    private fun rewindStep(rewind: Rewind): Long {
        val progress = ((timeSource.nowMs() - rewind.startedAtMs).toFloat() / REWIND_ANIMATION_MS).coerceIn(0f, 1f)
        val eased = progress * progress * (3 - 2 * progress) // começa e termina devagar, como uma fita
        songTimeMs = rewind.fromMs + ((rewind.toMs - rewind.fromMs) * eased).roundToLong()
        if (progress >= 1f) {
            this.rewind = null
            player.play()
            syncClock()
            songTimeMs = clock.positionMs()
        }
        return songTimeMs
    }

    /**
     * Dedo [pointer] tocou na [lane]. A partida guarda a trilha de cada dedo aceito: um dedo tocado durante a pausa
     * (ignorado) ou que já estava na tela quando ela pausou (esquecido) não solta, depois, a trilha de outro dedo.
     */
    fun pressPointer(pointer: Long, lane: Int, lateByMs: Long = 0) {
        if (isPaused || rewind != null) return
        pointerLanes[pointer] = lane
        press(lane, lateByMs)
    }

    fun releasePointer(pointer: Long, lateByMs: Long = 0) {
        val lane = pointerLanes.remove(pointer) ?: return
        release(lane, lateByMs)
    }

    /**
     * Toque na [lane]; [lateByMs] é quanto o toque esperou até chegar aqui (o julgamento usa o instante do dedo).
     * Privado: só por [pressPointer], que guarda a trilha de cada dedo (direto, desalinharia o mapa de dedos).
     */
    private fun press(lane: Int, lateByMs: Long) {
        if (isPaused || rewind != null) return
        fingers[lane]++
        judgePress(lane, lateByMs)
    }

    /** O julgamento do toque na [lane] (já apertada): acerto, ou erro sem nota. */
    private fun judgePress(lane: Int, lateByMs: Long) {
        val judgment = engine.onPress(lane, clock.positionMs() - lateByMs.coerceAtLeast(0))
        // O toque pode ter marcado como erro uma nota anterior (a do quadro tem folga): conta aqui, para o quadro
        // seguinte não tocar o erro depois do acerto nem calar a guitarra que este acerto ligou.
        val missedEarlierNote = engine.count(Judgment.MISS) > missesHeard
        missesHeard = engine.count(Judgment.MISS)
        if (judgment == null) {
            onMiss()
            return
        }
        onHit(listOf(lane), judgment, missedEarlierNote)
    }

    /** Resposta do acerto nas [lanes]: guitarra ou som, chama e o julgamento na tela. */
    private fun onHit(lanes: Collection<Int>, judgment: Judgment, missedEarlierNote: Boolean) {
        if (missedEarlierNote) sfx.miss()
        // Com a guitarra separada, ela mesma é a resposta do acerto (como no Guitar Hero).
        when {
            guitar != null -> guitar.setGuitarAudible(true)
            hitSounds -> lanes.forEach(sfx::hit)
        }
        val now = timeSource.nowMs()
        lanes.forEach { flameStartedAt[it] = now }
        lastJudgment = judgment
        lastJudgmentAt = now
    }

    /**
     * Cor [lane] apertada no teclado (PC, com a palheta): fica segurada, e acerta sem palhetar o tap ou o HOPO que
     * bater com as cores seguradas.
     */
    fun fretDown(lane: Int, lateByMs: Long = 0) {
        if (!heldFrets.add(lane)) return
        // Apertada na pausa ou na fita: fica segurada, para a palhetada depois, sem julgar agora.
        if (isPaused || rewind != null) return
        engageHeldFrets()
        if (strum) judgeWithoutStrum(lateByMs) else judgePress(lane, lateByMs)
    }

    /** Cor [lane] solta: encerra a nota longa dela e pode acertar o HOPO de baixo (pull-off). */
    fun fretUp(lane: Int, lateByMs: Long = 0) {
        if (!heldFrets.remove(lane)) return
        // Com um dedo ainda na trilha (celular com teclado), a trilha continua apertada.
        if (engagedFrets.remove(lane)) release(lane, lateByMs)
        if (strum && !isPaused && rewind == null) judgeWithoutStrum(lateByMs)
    }

    /** Palhetada (PC): julga o acorde com as cores seguradas; errada, toca o erro e cala a guitarra. */
    fun strum(lateByMs: Long = 0) {
        if (!strum || isPaused || rewind != null) return
        engageHeldFrets()
        val judgments = engine.onStrum(heldFrets.toSet(), clock.positionMs() - lateByMs.coerceAtLeast(0))
        val missedEarlierNote = engine.count(Judgment.MISS) > missesHeard
        missesHeard = engine.count(Judgment.MISS)
        when {
            judgments == null -> onMiss()
            judgments.isNotEmpty() -> onHit(hitLanes(judgments.size), judgments.first(), missedEarlierNote)
        }
    }

    /** Solta todas as cores seguradas (a janela perdeu o foco: as teclas não vão subir). */
    fun releaseAllFrets() {
        heldFrets.toList().forEach { fretUp(it) }
    }

    /** As cores seguradas na pausa ou na fita passam a valer na trilha (sem julgar: a palhetada julga). */
    private fun engageHeldFrets() {
        if (engagedFrets.size == heldFrets.size || isPaused) return
        for (lane in heldFrets) {
            if (engagedFrets.add(lane)) fingers[lane]++
        }
    }

    private fun judgeWithoutStrum(lateByMs: Long) {
        val judgments = engine.onFretsChanged(heldFrets.toSet(), clock.positionMs() - lateByMs.coerceAtLeast(0))
        val missedEarlierNote = engine.count(Judgment.MISS) > missesHeard
        missesHeard = engine.count(Judgment.MISS)
        if (!judgments.isNullOrEmpty()) onHit(hitLanes(judgments.size), judgments.first(), missedEarlierNote)
    }

    /** As cores do acerto: a de cima numa nota simples (as de baixo são apoio), todas num acorde. */
    private fun hitLanes(notes: Int): Collection<Int> = if (notes == 1) listOf(heldFrets.max()) else heldFrets.toList()

    /** A nota passou da hora sem ser tocada: a guitarra cala já (o erro em si só vem no fim da janela). */
    private fun silenceGuitarForOverdueNote() {
        val overdue = guitar != null && engine.hasOverdueNote(songTimeMs)
        if (overdue && !silencedForOverdueNote) guitar?.setGuitarAudible(false)
        silencedForOverdueNote = overdue
    }

    private fun onMiss() {
        sfx.miss()
        guitar?.setGuitarAudible(false)
    }

    private fun release(lane: Int, lateByMs: Long) {
        if (fingers[lane] == 0) return
        fingers[lane]--
        if (fingers[lane] == 0) engine.onRelease(lane, clock.positionMs() - lateByMs.coerceAtLeast(0))
    }

    fun isPressed(lane: Int): Boolean = fingers[lane] > 0

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
        waitingForSound = player.isStarting
        clock.sync(player.positionMs, !player.isPlaying || waitingForSound, now)
        lastSyncAt = now
    }

    companion object {
        const val SYNC_INTERVAL_MS = 500L

        /** Uns dois quadros: o toque que chega depois do quadro que passou da janela ainda é julgado. */
        const val MISS_GRACE_MS = 32L
        const val REWIND_ANIMATION_MS = 600L
        const val END_GRACE_MS = 3_000L
        private const val MISS_TAIL_MS = 300L
        private const val NEVER = Long.MIN_VALUE
    }
}
