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

    @Test
    fun adjustingTheOffsetWhilePausedKeepsTheHighwayStillAndAppliesOnResume() {
        player.positionMs = 1_000
        val session = session(note(10_000))
        session.pause()

        session.adjustOffset(-10)
        session.adjustOffset(-10)

        assertEquals(1_000L, session.tick()) // pausado: a pista não anda
        session.resume()
        assertEquals(980L, session.tick())
    }

    @Test
    fun adjustingTheOffsetWhilePlayingShiftsTheSongTimeImmediately() {
        player.positionMs = 1_000
        val session = session(note(10_000))

        session.adjustOffset(-10)

        assertEquals(990L, session.tick())
    }

    @Test
    fun withoutHitSoundsAHitIsSilentButMissesStillSound() {
        player.positionMs = 1_000
        val session = GameSession(listOf(note(1_000)), player, sfx, time, audioOffsetMs = 0, hitSounds = false)
            .also { it.start() }

        session.press(0)
        session.press(1)

        assertEquals(emptyList<Int>(), sfx.hitLanes)
        assertEquals(Judgment.PERFECT, session.lastJudgment)
        assertEquals(1, sfx.misses)
    }

    @Test
    fun whileTheSongIsStartingTheHighwayWaitsAndThenFollowsTheSoundAtOnce() {
        player.positionMs = 30_000
        val session = session(note(60_000))
        session.pause()
        player.isStarting = true

        session.resume()
        time.now = 200
        assertEquals(30_000L, session.tick()) // o som ainda não voltou: a pista não anda
        assertFalse(session.isPaused) // e não é uma pausa (sem menu)

        player.isStarting = false
        player.positionMs = 30_010
        time.now = 210 // o próximo quadro, bem antes da sincronização periódica de 500 ms
        assertEquals(30_010L, session.tick())
        time.now = 310
        assertEquals(30_110L, session.tick())
    }

    @Test
    fun resumingRewindsLikeATapeAndThenPlays() {
        player.positionMs = 30_000
        val session = GameSession(listOf(note(60_000)), player, sfx, time, audioOffsetMs = 0, rewindOnResumeMs = 500)
            .also { it.start() }
        session.pause()

        session.resume()

        assertEquals(listOf(29_500L), player.seeks)
        assertFalse(player.isPlaying) // a música espera a fita voltar
        assertFalse(session.isPaused) // e não é uma pausa (sem menu)
        assertEquals(30_000L, session.tick())
        time.now += GameSession.REWIND_ANIMATION_MS / 2
        val halfway = session.tick()
        assertTrue(halfway in 29_501L..29_999L) // volta aos poucos, sem pular
        time.now += GameSession.REWIND_ANIMATION_MS / 2
        assertEquals(29_500L, session.tick())
        assertTrue(player.isPlaying)
        time.now += 100
        assertEquals(29_600L, session.tick())
    }

    @Test
    fun tapsDuringTheRewindAreIgnored() {
        player.positionMs = 30_000
        val session = GameSession(listOf(note(29_600)), player, sfx, time, audioOffsetMs = 0, rewindOnResumeMs = 500)
            .also { it.start() }
        session.pause()
        session.resume()

        session.press(0)

        assertEquals(0, sfx.misses)
        assertEquals(null, session.lastJudgment)
    }

    @Test
    fun theRewindStopsAtTheStartOfTheSong() {
        player.positionMs = 200
        val session = GameSession(listOf(note(60_000)), player, sfx, time, audioOffsetMs = 0, rewindOnResumeMs = 500)
            .also { it.start() }
        session.pause()

        session.resume()

        assertEquals(listOf(0L), player.seeks)
    }

    @Test
    fun withoutRewindResumingKeepsThePosition() {
        player.positionMs = 30_000
        val session = session(note(60_000))
        session.pause()

        session.resume()

        assertEquals(emptyList<Long>(), player.seeks)
    }

    @Test
    fun withTheSeparateGuitarANoteNotYetHitSilencesItRightAfterItsTime() {
        val session = stemSession(note(1_000))
        stems.song.positionMs = 1_060
        time.now = SYNC

        session.tick()
        session.tick()

        assertEquals(listOf(false), stems.audible) // uma vez só, sem esperar o fim da janela
        assertEquals(0, sfx.misses) // ainda não é erro: dá para acertar atrasado
        session.press(0)
        assertEquals(listOf(false, true), stems.audible)
    }

    @Test
    fun anAdjustmentMadeWhilePausedAppliesWhenTheMusicResumesFromOutsideTheGame() {
        player.positionMs = 30_000
        val session = session(note(60_000))
        session.pause()
        session.adjustOffset(1_000)

        player.isPlaying = true // o Spotify voltou pela notificação ou pelo fone, sem passar por resume()
        time.now = SYNC

        assertEquals(31_000L, session.tick())
    }
}
