package com.rodrigomachado.riff.desktop.pad

import com.rodrigomachado.riff.core.model.ControlAction
import com.rodrigomachado.riff.core.model.Controls
import com.rodrigomachado.riff.core.ui.input.PadAction
import com.rodrigomachado.riff.core.ui.input.PadEvent
import kotlin.math.abs

/**
 * As leituras do controle viram mudanças com os botões escolhidos ([pad]; de fábrica: △ verde, ○ vermelho, □ amarelo,
 * ✕ azul, R1 laranja, Start pausa, direcional ↑/↓ — ou o chapéu, no modo cru — palheta). O analógico esquerdo palheta
 * sempre, além da escolha; uma palhetada por borda de descida de qualquer um. Guarda o que estava apertado (e a
 * histerese do analógico) entre as leituras.
 */
class PadMapper {
    /** Os botões escolhidos (`Controls.pad`); trocá-los no meio vale a partir da próxima leitura. */
    var pad: Map<ControlAction, String> = Controls.DEFAULT_PAD

    private var held = emptySet<PadAction>()
    private var strummers = emptySet<Strummer>()

    /** As mudanças da leitura [current] (nulo = o controle sumiu: solta tudo), no instante [atNanos]. */
    fun changes(current: PadSnapshot?, atNanos: Long): List<PadEvent> {
        val nowHeld = current?.let(::heldIn).orEmpty()
        val nowStrummers = current?.let(::strummersIn).orEmpty()
        val events = HELD_ACTIONS.mapNotNull { (_, action) ->
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

    private fun heldIn(snapshot: PadSnapshot): Set<PadAction> =
        HELD_ACTIONS.filter { (choice, _) -> snapshot.isPressed(pad[choice]) }.mapTo(mutableSetOf()) { it.second }

    private fun strummersIn(snapshot: PadSnapshot): Set<Strummer> = buildSet {
        if (snapshot.isPressed(pad[ControlAction.STRUM_UP])) add(Strummer.BUTTON_UP)
        if (snapshot.isPressed(pad[ControlAction.STRUM_DOWN])) add(Strummer.BUTTON_DOWN)
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

    private enum class Strummer { BUTTON_UP, BUTTON_DOWN, STICK_UP, STICK_DOWN }

    private companion object {
        const val STICK_ON = 0.5f
        const val STICK_OFF = 0.3f

        /** As ações seguradas (com soltura): a escolha e a ação da partida, na ordem dos eventos de uma leitura. */
        val HELD_ACTIONS = listOf(
            ControlAction.FRET_1 to PadAction.FRET_1,
            ControlAction.FRET_2 to PadAction.FRET_2,
            ControlAction.FRET_3 to PadAction.FRET_3,
            ControlAction.FRET_4 to PadAction.FRET_4,
            ControlAction.FRET_5 to PadAction.FRET_5,
            ControlAction.PAUSE to PadAction.PAUSE,
        )
    }
}
