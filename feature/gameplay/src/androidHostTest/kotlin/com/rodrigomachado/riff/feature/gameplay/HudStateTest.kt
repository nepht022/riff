package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.runtime.snapshots.Snapshot
import com.rodrigomachado.riff.core.model.Judgment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HudStateTest {
    private val time = FakeTime()
    private val player = FakeSongPlayer()

    private fun writesDuring(block: () -> Unit): Int {
        var writes = 0
        val handle = Snapshot.registerGlobalWriteObserver { writes++ }
        try {
            block()
        } finally {
            handle.dispose()
        }
        return writes
    }

    @Test
    fun nothingChangedMeansNothingIsWrittenSoTheScoreDoesNotRecompose() {
        val session = GameSession(listOf(note(5_000)), player, FakeSfxPlayer(), time, audioOffsetMs = 0).also {
            it.start()
        }
        val hud = HudState()
        hud.update(session, withoutSeparateGuitar = false)

        assertEquals(0, writesDuring { hud.update(session, withoutSeparateGuitar = false) })
    }

    @Test
    fun aHitUpdatesScoreComboAndJudgment() {
        player.positionMs = 1_000
        val session = GameSession(listOf(note(1_000)), player, FakeSfxPlayer(), time, audioOffsetMs = 0).also {
            it.start()
        }
        val hud = HudState()
        hud.update(session, withoutSeparateGuitar = false)

        session.press(0)
        val writes = writesDuring { hud.update(session, withoutSeparateGuitar = false) }

        assertTrue(writes > 0)
        assertEquals(1, hud.combo)
        assertEquals(Judgment.PERFECT, hud.judgment)
        assertTrue(hud.showingJudgment)
        assertTrue(hud.score > 0)
    }
}
