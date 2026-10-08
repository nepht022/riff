package com.rodrigomachado.riff.feature.gameplay

import com.rodrigomachado.riff.core.model.Judgment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionTest {
    private val time = FakeTime()
    private val player = FakeSongPlayer()
    private val sfx = FakeSfxPlayer()

    private fun session(vararg notes: com.rodrigomachado.riff.core.model.Note) =
        GameSession(notes.toList(), player, sfx, time, audioOffsetMs = 0).also { it.start() }

    @Test
    fun hitPlaysTheHitSoundLightsTheFlameAndShowsTheJudgment() {
        player.positionMs = 1_000
        val session = session(note(1_000, lane = 2))

        session.press(2)

        assertEquals(listOf(2), sfx.hitLanes) // o som da trilha tocada
        assertEquals(Judgment.PERFECT, session.lastJudgment)
        assertEquals(0f, session.flameProgress(2), 0.001f)
        time.now = 125
        assertEquals(0.5f, session.flameProgress(2), 0.001f)
        assertTrue(session.flameProgress(0) < 0f)
    }

    @Test
    fun tapWithNoNoteNearPlaysTheMissSoundWithoutJudgmentText() {
        val session = session(note(5_000))

        session.press(0)

        assertEquals(1, sfx.misses)
        assertEquals(null, session.lastJudgment)
        assertTrue(session.isPressed(0))
    }

    @Test
    fun noteThatPassesUnplayedPlaysTheMissSoundOnce() {
        val session = session(note(1_000))
        player.positionMs = 1_500
        time.now = SYNC

        session.tick()
        time.now = SYNC + 16
        session.tick()

        assertEquals(1, sfx.misses)
        assertEquals(1, session.engine.count(Judgment.MISS))
    }

    @Test
    fun clockFollowsThePlayerAndResyncsPeriodically() {
        val session = session(note(10_000))
        time.now = 400
        assertEquals(400L, session.tick()) // extrapolado desde o início

        player.positionMs = 8_000 // o usuário avançou a música
        time.now = SYNC + 1
        assertEquals(8_000L, session.tick())
    }

    @Test
    fun pausingStopsTheSongReleasesHeldLanesAndIgnoresTaps() {
        player.positionMs = 1_000
        val session = session(note(1_000, lane = 1, sustainMs = 2_000))
        session.press(1)

        session.pause()

        assertFalse(player.isPlaying)
        assertTrue(session.isPaused)
        assertFalse(session.isPressed(1))
        assertTrue(session.engine.state.activeSustains.isEmpty())
        session.press(3)
        assertFalse(session.isPressed(3))

        session.resume()
        assertTrue(player.isPlaying)
    }

    @Test
    fun finishesWhenTheSongEnds() {
        val session = session(note(60_000))
        assertFalse(session.isFinished)

        player.isCompleted = true

        assertTrue(session.isFinished)
    }

    @Test
    fun finishesAfterTheGracePeriodOnceEveryNoteIsJudged() {
        player.positionMs = 1_000
        val session = session(note(1_000))
        session.press(0)
        assertFalse(session.isFinished)

        player.positionMs = 1_000 + GameSession.END_GRACE_MS + 1
        time.now = SYNC
        session.tick()

        assertTrue(session.isFinished)
    }

    private companion object {
        const val SYNC = GameSession.SYNC_INTERVAL_MS
    }

    private val stems = FakeStemPlayer()

    private fun stemSession(vararg notes: com.rodrigomachado.riff.core.model.Note) =
        GameSession(notes.toList(), stems, sfx, time, audioOffsetMs = 0).also { it.start() }

    @Test
    fun withTheSeparateGuitarAHitBringsItBackWithoutTheHitSound() {
        stems.song.positionMs = 1_000
        val session = stemSession(note(1_000, lane = 1))

        session.press(1)

        assertEquals(listOf(true), stems.audible)
        assertEquals(0, sfx.hits)
    }

    @Test
    fun withTheSeparateGuitarATapWithoutANoteCutsItAndPlaysTheMissSound() {
        val session = stemSession(note(5_000))

        session.press(0)

        assertEquals(listOf(false), stems.audible)
        assertEquals(1, sfx.misses)
    }

    @Test
    fun withTheSeparateGuitarANoteThatPassesCutsIt() {
        val session = stemSession(note(1_000))
        stems.song.positionMs = 1_500
        time.now = SYNC

        session.tick()

        assertEquals(listOf(false), stems.audible)
        assertEquals(1, sfx.misses)
    }
}
