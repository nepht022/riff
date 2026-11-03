package com.rodrigomachado.riff.desktop.pad

import com.rodrigomachado.riff.core.ui.input.PadAction
import com.rodrigomachado.riff.core.ui.input.PadEvent
import kotlin.math.abs

/**
 * As leituras do controle viram mudanças com os botões de fábrica: △ verde, ○ vermelho, □ amarelo, ✕ azul, R1 laranja,
 * Start pausa; a palheta é o direcional ↑/↓ (ou o chapéu, no modo cru) e o analógico esquerdo, uma palhetada por
 * borda de descida de qualquer um. Guarda o que estava apertado (e a histerese do analógico) entre as leituras.
 */
class PadMapper {
    private var held = emptySet<PadAction>()
    private var strummers = emptySet<Strummer>()

    /** As mudanças da leitura [current] (nulo = o controle sumiu: solta tudo), no instante [atNanos]. */
    fun changes(current: PadSnapshot?, atNanos: Long): List<PadEvent> {
        val nowHeld = current?.let(::heldIn).orEmpty()
        val nowStrummers = current?.let(::strummersIn).orEmpty()
        val events = HELD_ACTIONS.mapNotNull { action ->
            when (action) {
                in nowHeld - held -> PadEvent(action, down = true, atNanos = atNanos)
                in held - nowHeld -> PadEvent(action, down = false, atNanos = atNanos)
                else -> null
            }
        } + (nowStrummers - strummers).map { PadEvent(PadAction.STRUM, down = true, atNanos = atNanos) }
        held = nowHeld
        strummers = nowStrummers
        return events
    }

    private fun heldIn(snapshot: PadSnapshot): Set<PadAction> {
        val buttons = if (snapshot.mapped) MAPPED_BUTTONS else RAW_BUTTONS
        return buttons.filterKeys { it in snapshot.buttons }.values.toSet()
    }

    private fun strummersIn(snapshot: PadSnapshot): Set<Strummer> = buildSet {
        val (up, down) = if (snapshot.mapped) {
            MappedButton.DPAD_UP to MappedButton.DPAD_DOWN
        } else {
            RawButton.UP to RawButton.DOWN
        }
        if (up in snapshot.buttons) add(Strummer.DPAD_UP)
        if (down in snapshot.buttons) add(Strummer.DPAD_DOWN)
        if (snapshot.hat and HAT_UP != 0) add(Strummer.HAT_UP)
        if (snapshot.hat and HAT_DOWN != 0) add(Strummer.HAT_DOWN)
        stick(snapshot.leftY)?.let(::add)
    }

    /** O analógico liga em |y| ≥ 0,5 e só desliga abaixo de 0,3: tremer perto do limiar não palheta de novo. */
    private fun stick(y: Float): Strummer? {
        val direction = if (y < 0) Strummer.STICK_UP else Strummer.STICK_DOWN
        return when {
            abs(y) >= STICK_ON -> direction
            abs(y) >= STICK_OFF && direction in strummers -> direction
            else -> null
        }
    }

    private enum class Strummer { DPAD_UP, DPAD_DOWN, HAT_UP, HAT_DOWN, STICK_UP, STICK_DOWN }

    private companion object {
        const val STICK_ON = 0.5f
        const val STICK_OFF = 0.3f

        /** As ações seguradas (com soltura), na ordem dos eventos de uma mesma leitura. */
        val HELD_ACTIONS = PadAction.entries - PadAction.STRUM

        val MAPPED_BUTTONS = mapOf(
            MappedButton.Y to PadAction.FRET_1,
            MappedButton.B to PadAction.FRET_2,
            MappedButton.X to PadAction.FRET_3,
            MappedButton.A to PadAction.FRET_4,
            MappedButton.RIGHT_BUMPER to PadAction.FRET_5,
            MappedButton.START to PadAction.PAUSE,
        )
        val RAW_BUTTONS = mapOf(
            RawButton.TRIANGLE to PadAction.FRET_1,
            RawButton.CIRCLE to PadAction.FRET_2,
            RawButton.SQUARE to PadAction.FRET_3,
            RawButton.CROSS to PadAction.FRET_4,
            RawButton.R1 to PadAction.FRET_5,
            RawButton.START to PadAction.PAUSE,
        )
    }
}
