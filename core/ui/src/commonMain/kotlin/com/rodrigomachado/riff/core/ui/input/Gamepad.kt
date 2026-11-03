package com.rodrigomachado.riff.core.ui.input

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** O que um botão do controle faz na partida: as cinco cores, a palheta e a pausa. */
enum class PadAction(
    /** A raia da cor (0 = verde .. 4 = laranja); nulo para a palheta e a pausa. */
    val fretLane: Int?,
) {
    FRET_1(0),
    FRET_2(1),
    FRET_3(2),
    FRET_4(3),
    FRET_5(4),
    STRUM(null),
    PAUSE(null),
}

/**
 * Uma mudança no controle: [action] apertada ([down]) ou solta, no instante [atNanos] (`System.nanoTime()` da leitura
 * em que a mudança apareceu). A palheta só desce: cada palhetada é um evento com `down = true`.
 */
data class PadEvent(val action: PadAction, val down: Boolean, val atNanos: Long)

/** O controle do PC: as mudanças, na ordem em que foram lidas, e se há um controle ligado. */
interface Gamepad {
    val events: Flow<PadEvent>
    val connected: StateFlow<Boolean>

    /**
     * Cada botão que desce, como o texto gravado na escolha: `m:<índice>` num controle mapeado pelo layout do GLFW,
     * `r:<índice>` (cru) ou `h:up`/`h:down` (chapéu) num controle fora da base do SDL.
     */
    val presses: Flow<String>
}

/** O controle do PC; nulo no celular (lá não há leitura de controle). */
val LocalGamepad = staticCompositionLocalOf<Gamepad?> { null }

/** O relógio dos eventos do controle (`PadEvent.atNanos`): quem recebe um evento mede quanto ele esperou. */
expect fun nowNanos(): Long
