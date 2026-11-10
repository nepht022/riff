package com.rodrigomachado.riff.core.room

import com.rodrigomachado.riff.core.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** As regras do Começar e de quem joga a rodada, sobre a [Room] pura (sem banco). */
class RoomRoundTest {
    private fun player(
        uid: String,
        ready: Boolean = true,
        song: SongStatus? = SongStatus.HAS,
        difficulty: Difficulty? = Difficulty.HARD,
        gone: Boolean = false,
        loadedRound: Int? = null,
        leftRound: Int? = null,
    ) = RoomPlayer(
        uid = uid,
        name = uid,
        device = RoomDevice.PHONE,
        joinedAt = 0,
        seenAt = 0,
        gone = gone,
        difficulty = difficulty,
        ready = ready,
        song = song,
        loadedRound = loadedRound,
        leftRound = leftRound,
    )

    private fun room(
        vararg players: RoomPlayer,
        state: RoomState = RoomState.LOBBY,
        round: Int = 0,
        song: RoomSong? = SONG,
        firstReadyAt: Long? = T0,
    ) = Room(
        code = "ABCD",
        owner = "ana",
        players = players.toList(),
        me = "ana",
        song = song,
        round = round,
        state = state,
        startAt = null,
        firstReadyAt = firstReadyAt,
    )

    @Test
    fun oneReadyCannotStart() {
        assertFalse(room(player("ana")).canStart(T0 + HOUR))
    }

    @Test
    fun twoReadyCanStart() {
        assertTrue(room(player("ana"), player("bia")).canStart(T0))
    }

    @Test
    fun aPlayerWithTheSongNotReadyWaits30sFromTheFirstReady() {
        val room = room(player("ana"), player("bia"), player("caio", ready = false))

        assertFalse(room.canStart(T0 + 29_999))
        assertTrue(room.canStart(T0 + 30_000))
    }

    @Test
    fun withoutAFirstReadyTimeTheWaitNeverEnds() {
        val room = room(player("ana"), player("bia"), player("caio", ready = false), firstReadyAt = null)

        assertFalse(room.canStart(T0 + HOUR))
    }

    @Test
    fun missingOrDownloadingSongDoesNotBlock() {
        val missing = room(player("ana"), player("bia"), player("caio", ready = false, song = SongStatus.MISSING))
        val unknown = room(player("ana"), player("bia"), player("caio", ready = false, song = null))
        val downloading =
            room(player("ana"), player("bia"), player("caio", ready = false, song = SongStatus.DOWNLOADING))

        assertTrue(missing.canStart(T0))
        assertTrue(unknown.canStart(T0))
        assertTrue(downloading.canStart(T0))
    }

    @Test
    fun readyWithoutTheSongOrDifficultyDoesNotCount() {
        assertFalse(room(player("ana"), player("bia", song = SongStatus.MISSING)).canStart(T0 + HOUR))
        assertFalse(room(player("ana"), player("bia", difficulty = null)).canStart(T0 + HOUR))
    }

    @Test
    fun goneDoesNotBlockNorCount() {
        assertTrue(room(player("ana"), player("bia"), player("caio", ready = false, gone = true)).canStart(T0))
        assertFalse(room(player("ana"), player("bia", gone = true)).canStart(T0 + HOUR))
    }

    @Test
    fun noSongOrOutsideTheLobbyCannotStart() {
        assertFalse(room(player("ana"), player("bia"), song = null).canStart(T0))
        assertFalse(room(player("ana"), player("bia"), state = RoomState.COUNTDOWN, round = 1).canStart(T0))
        assertFalse(room(player("ana"), player("bia"), state = RoomState.PLAYING, round = 1).canStart(T0))
    }

    @Test
    fun playersInRoundAreAliveReadyWithTheSongAndDidNotLeave() {
        val room = room(
            player("ana"),
            player("bia", leftRound = 2),
            player("caio", ready = false),
            player("davi", gone = true),
            player("eva", leftRound = 1, song = SongStatus.HAS),
            state = RoomState.COUNTDOWN,
            round = 2,
        )

        assertEquals(listOf("ana", "eva"), room.playersInRound().map { it.uid })
    }

    private companion object {
        const val T0 = 1_800_000_000_000L
        const val HOUR = 3_600_000L
        val SONG = RoomSong(
            chartId = "a".repeat(40),
            title = "Música",
            artist = "Banda",
            difficulties = setOf(Difficulty.HARD),
            durationMs = null,
            encore = null,
        )
    }
}
