package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rodrigomachado.riff.core.model.Judgment

internal const val JUDGMENT_VISIBLE_MS = 500L
internal const val NOTICE_MS = 4_000L
private const val JUDGMENT_BOUNCE_MS = 120f
private const val JUDGMENT_BOUNCE_SCALE = 0.25f

/**
 * O que o placar mostra, em estado do Compose. [update] roda a cada quadro, mas só escreve o que mudou: o
 * placar recompõe quando a pontuação, o combo ou o julgamento mudam, não a cada quadro.
 */
internal class HudState {
    var score by mutableLongStateOf(0L)
        private set
    var multiplier by mutableIntStateOf(1)
        private set
    var combo by mutableIntStateOf(0)
        private set
    var judgment by mutableStateOf<Judgment?>(null)
        private set
    var showingJudgment by mutableStateOf(false)
        private set
    var showingNotice by mutableStateOf(false)
        private set

    fun update(session: GameSession, withoutSeparateGuitar: Boolean) {
        val engine = session.engine
        score = engine.score
        multiplier = engine.multiplier
        combo = engine.combo
        judgment = session.lastJudgment
        showingJudgment = judgment != null && session.judgmentAgeMs() < JUDGMENT_VISIBLE_MS
        showingNotice = withoutSeparateGuitar && session.songTimeMs < NOTICE_MS
    }
}

/** Salto do julgamento logo depois do toque (1 = tamanho normal). */
internal fun judgmentBounce(ageMs: Long, reducedMotion: Boolean): Float =
    if (reducedMotion) 1f else 1f + JUDGMENT_BOUNCE_SCALE * (1f - (ageMs / JUDGMENT_BOUNCE_MS).coerceAtMost(1f))
