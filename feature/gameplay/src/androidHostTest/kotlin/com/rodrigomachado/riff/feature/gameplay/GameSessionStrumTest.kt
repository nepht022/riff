package com.rodrigomachado.riff.feature.gameplay

import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A partida com o teclado e a palheta (PC): segurar as cores e palhetar. */
class GameSessionStrumTest {
    private val time = FakeTime()
    private val player = FakeSongPlayer()
    private val sfx = FakeSfxPlayer()

    private fun session(vararg notes: Note, songPlayer: com.rodrigomachado.riff.core.audio.SongPlayer = player) =
        GameSession(notes.toList(), songPlayer, sfx, time, audioOffsetMs = 0).also { it.start() }

    @Test
    fun aStrumWithTheFretHeldHitsAndLightsTheButton() {
        player.positionMs = 1_000
        val session = session(note(1_000, lane = 0))

        session.fretDown(0)
        session.strum()

        assertEquals(Judgment.PERFECT, session.lastJudgment)
        assertTrue(session.isPressed(0))
        assertEquals(0f, session.flameProgress(0), 0.001f)
        session.fretUp(0)
        assertFalse(session.isPressed(0))
    }

    @Test
    fun anOverstrumPlaysTheMissSoundAndCutsTheGuitar() {
        val stems = FakeStemPlayer(player)
        player.positionMs = 5_000
        val session = session(note(1_000, lane = 0), note(9_000, lane = 0), songPlayer = stems)

        session.strum()

        assertEquals(1, sfx.misses)
        assertEquals(false, stems.audible.last())
    }

    @Test
    fun releasingTheFretEndsItsLongNote() {
        player.positionMs = 1_000
        val session = session(note(1_000, lane = 2, sustainMs = 2_000))
        session.fretDown(2)
        session.strum()
        player.positionMs = 1_500
        time.now = GameSession.SYNC_INTERVAL_MS
        session.tick()

        session.fretUp(2)
        val atRelease = session.engine.score
        player.positionMs = 2_500
        time.now = GameSession.SYNC_INTERVAL_MS * 2
        session.tick()

        assertEquals(atRelease, session.engine.score)
    }

    @Test
    fun aHopoIsHitByThePressAlone() {
        player.positionMs = 1_000
        val session = session(note(1_000, lane = 0), Note(1_200, 1, 0, isHopo = true))
        session.fretDown(0)
        session.strum()
        session.fretUp(0)
        player.positionMs = 1_200
        time.now = GameSession.SYNC_INTERVAL_MS
        session.tick()

        session.fretDown(1)

        assertEquals(2, session.engine.combo)
    }

    @Test
    fun releasingAllFretsLeavesNothingHeld() {
        val session = session(note(9_000, lane = 0))
        session.fretDown(0)
        session.fretDown(3)

        session.releaseAllFrets()

        assertFalse(session.isPressed(0))
        assertFalse(session.isPressed(3))
    }

    /** Pausar (Esc) com uma cor segurada a solta de vez: a tecla subindo depois não desconta um aperto novo. */
    @Test
    fun pausingLetsGoOfTheHeldFrets() {
        val session = session(note(9_000, lane = 0))
        session.fretDown(0)

        session.pause()
        assertFalse(session.isPressed(0))
        session.fretUp(0) // a tecla sobe com a pausa aberta
        player.play() // volta a tocar (sem a volta de 0,5 s da retomada)
        session.fretDown(0)

        assertTrue(session.isPressed(0))
    }
}
