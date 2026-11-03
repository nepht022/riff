package com.rodrigomachado.riff.desktop.pad

import com.rodrigomachado.riff.core.model.ControlAction
import com.rodrigomachado.riff.core.model.Controls
import com.rodrigomachado.riff.core.ui.input.PadAction
import com.rodrigomachado.riff.core.ui.input.PadEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/** Os botões de fábrica (△ ○ □ ✕ R1, direcional/analógico, Start), no layout de controle do GLFW e no cru do DS3. */
class PadMapperTest {
    private val mapper = PadMapper()

    /** Um controle mapeado: os botões do layout (os crus não importam aqui). */
    private fun mapped(vararg buttons: Int, leftY: Float = 0f) =
        PadSnapshot(raw = emptySet(), leftY = leftY, layout = buttons.toSet())

    private fun raw(vararg buttons: Int, hat: Int = 0) = PadSnapshot(raw = buttons.toSet(), hat = hat)

    private fun choosing(vararg choices: Pair<ControlAction, String>) {
        mapper.pad = choices.fold(Controls()) { controls, (action, button) -> controls.withPad(action, button) }.pad
    }

    private fun down(action: PadAction, at: Long) = PadEvent(action, down = true, atNanos = at)

    private fun up(action: PadAction, at: Long) = PadEvent(action, down = false, atNanos = at)

    /** As leituras em sequência (instantes 1, 2, 3...), e os eventos de cada uma. */
    private fun read(vararg snapshots: PadSnapshot?): List<List<PadEvent>> =
        snapshots.mapIndexed { index, snapshot -> mapper.changes(snapshot, index + 1L) }

    @Test
    fun triangleIsTheGreenFret() {
        assertEquals(listOf(emptyList(), listOf(down(PadAction.FRET_1, 2))), read(mapped(), mapped(MappedButton.Y)))
    }

    @Test
    fun r1IsTheOrangeFret() {
        assertEquals(
            listOf(listOf(down(PadAction.FRET_5, 1)), listOf(up(PadAction.FRET_5, 2))),
            read(mapped(MappedButton.RIGHT_BUMPER), mapped()),
        )
    }

    @Test
    fun dpadDownStrumsOncePerPress() {
        val held = mapped(MappedButton.DPAD_DOWN)
        assertEquals(listOf(listOf(down(PadAction.STRUM, 1)), emptyList()), read(held, held))
    }

    @Test
    fun theStickStrumsWithHysteresis() {
        val events = read(*listOf(0f, 0.6f, 0.4f, 0.6f, 0.2f, 0.6f).map { mapped(leftY = it) }.toTypedArray())
        assertEquals(
            listOf(emptyList(), listOf(down(PadAction.STRUM, 2)), emptyList(), emptyList(), emptyList()) +
                listOf(listOf(down(PadAction.STRUM, 6))),
            events,
        )
    }

    @Test
    fun theStickStrumsUpwardsToo() {
        assertEquals(listOf(listOf(down(PadAction.STRUM, 1))), read(mapped(leftY = -0.7f)))
    }

    @Test
    fun aStickDriftingNearTheMiddleDoesNotStrum() {
        val drift = listOf(0.2f, 0.45f, 0.3f, 0.45f, 0.2f, -0.45f, 0.45f).map { mapped(leftY = it) }
        assertEquals(drift.map { emptyList<PadEvent>() }, read(*drift.toTypedArray()))
    }

    @Test
    fun startPauses() {
        assertEquals(
            listOf(listOf(down(PadAction.PAUSE, 1)), listOf(up(PadAction.PAUSE, 2))),
            read(mapped(MappedButton.START), mapped()),
        )
    }

    /** Fora da base do SDL: a ordem crua do DualShock 3 (12 △, 14 ✕, 4 ↑) e o chapéu 0 dos clones. */
    @Test
    fun rawPadUsesTheRawButtonOrder() {
        assertEquals(
            listOf(
                listOf(down(PadAction.FRET_1, 1)),
                listOf(up(PadAction.FRET_1, 2), down(PadAction.FRET_4, 2)),
                listOf(up(PadAction.FRET_4, 3), down(PadAction.STRUM, 3)),
                emptyList(),
                listOf(down(PadAction.STRUM, 5)),
            ),
            read(
                raw(RawButton.TRIANGLE),
                raw(RawButton.CROSS),
                raw(RawButton.UP),
                raw(),
                raw(hat = HAT_UP),
            ),
        )
    }

    @Test
    fun rawPadHasEveryFactoryButton() {
        val buttons = listOf(
            RawButton.TRIANGLE,
            RawButton.CIRCLE,
            RawButton.SQUARE,
            RawButton.CROSS,
            RawButton.R1,
            RawButton.START,
            RawButton.DOWN,
        )
        val all = raw(*buttons.toIntArray())
        assertEquals(
            // As cinco cores e a pausa, na ordem das ações, e depois a palhetada.
            listOf(PadAction.FRET_1, PadAction.FRET_2, PadAction.FRET_3, PadAction.FRET_4, PadAction.FRET_5)
                .plus(PadAction.PAUSE)
                .plus(PadAction.STRUM)
                .map { down(it, 1) },
            read(all).single(),
        )
    }

    /** O cabo saiu com △ e R1 apertados: as cores são soltas (sem nota longa presa). */
    @Test
    fun aDisconnectedPadReleasesWhatWasHeld() {
        assertEquals(
            listOf(up(PadAction.FRET_1, 2), up(PadAction.FRET_5, 2)),
            read(mapped(MappedButton.Y, MappedButton.RIGHT_BUMPER), null)[1],
        )
    }

    /** Depois de religado, o direcional que já estava para baixo palheta de novo. */
    @Test
    fun aReconnectedPadStrumsAgain() {
        val held = mapped(MappedButton.DPAD_DOWN)
        assertEquals(listOf(down(PadAction.STRUM, 3)), read(held, null, held)[2])
    }

    /** ✕ (m:0) escolhido para o verde: vale no layout mapeado e, no cru, pela ordem do DualShock 3 (14). */
    @Test
    fun aChosenPadButtonPlaysItsFret() {
        choosing(ControlAction.FRET_1 to "m:0")

        assertEquals(
            listOf(
                listOf(down(PadAction.FRET_1, 1)),
                listOf(up(PadAction.FRET_1, 2)),
                listOf(down(PadAction.FRET_4, 3)),
                listOf(down(PadAction.FRET_1, 4), up(PadAction.FRET_4, 4)),
            ),
            read(mapped(MappedButton.A), mapped(), mapped(MappedButton.Y), raw(RawButton.CROSS)),
        )
    }

    /** O botão cru 7 escolhido para a palheta ↓: vale num controle cru e num mapeado (os crus vêm sempre). */
    @Test
    fun aChosenRawButtonStrums() {
        choosing(ControlAction.STRUM_DOWN to "r:7")
        val mappedWithRaw7 = PadSnapshot(raw = setOf(7), layout = emptySet())

        assertEquals(
            listOf(listOf(down(PadAction.STRUM, 1)), emptyList(), emptyList(), listOf(down(PadAction.STRUM, 4))),
            read(raw(7), raw(7), mapped(MappedButton.DPAD_DOWN), mappedWithRaw7),
        )
    }

    @Test
    fun theStickStillStrumsWithAnyChoice() {
        choosing(ControlAction.STRUM_UP to "r:1", ControlAction.STRUM_DOWN to "h:down")

        assertEquals(
            listOf(listOf(down(PadAction.STRUM, 1)), emptyList(), listOf(down(PadAction.STRUM, 3))),
            read(mapped(leftY = 0.7f), mapped(), raw(hat = HAT_DOWN)),
        )
    }

    /**
     * Controle cru: escolher △ (botão cru 12) para o Vermelho troca com o Verde (que é △ pelo padrão `m:3`): o mesmo
     * botão físico tem um nome só, e △ não aperta duas cores.
     */
    @Test
    fun choosingARawButtonUsedByAnotherActionSwapsThem() {
        val triangle = raw(RawButton.TRIANGLE).pressedSince(null).single()
        choosing(ControlAction.FRET_2 to triangle)

        val events = read(raw(), raw(RawButton.TRIANGLE), raw(), raw(RawButton.CIRCLE))

        assertEquals(listOf(down(PadAction.FRET_2, 2)), events[1])
        assertEquals(listOf(down(PadAction.FRET_1, 4)), events[3])
    }

    /** No cru, o direcional (botão ou chapéu) tem o nome do layout: escolhê-lo para uma cor tira-o da palheta. */
    @Test
    fun aRawDpadIsNamedLikeTheLayout() {
        assertEquals(listOf("m:11"), raw(RawButton.UP).pressedSince(null))
        assertEquals(listOf("m:13"), raw(hat = HAT_DOWN).pressedSince(null))
        assertEquals(listOf("r:9"), raw(9).pressedSince(null))
    }
}
