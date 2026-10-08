package com.rodrigomachado.riff.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rodrigomachado.riff.core.database.RiffDatabase
import com.rodrigomachado.riff.core.database.TrackMatchEntity
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MatchStatus
import com.rodrigomachado.riff.core.model.PlaybackState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomTrackMatchRepositoryTest {
    private lateinit var db: RiffDatabase
    private lateinit var charts: RoomChartRepository
    private lateinit var matches: RoomTrackMatchRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RiffDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        charts = RoomChartRepository(db.chartDao())
        matches = RoomTrackMatchRepository(db.trackMatchDao(), charts)
    }

    @After
    fun tearDown() = db.close()

    private fun chart(id: String, title: String, artist: String, durationMs: Long? = null) =
        Chart(id, title, artist, null, null, durationMs, "content://$id", ChartFormat.CHART, setOf(Difficulty.EXPERT))

    private fun track(title: String, artist: String, durationMs: Long = 355_000) =
        PlaybackState("spotify:track:x", title, artist, durationMs, 0, false, 0)

    @Test
    fun closeTitleAndArtistMatchAutomatically() = runTest {
        val queen = chart("a", "Bohemian Rhapsody", "Queen")
        charts.saveScanned(queen, 1)

        assertEquals(
            MatchStatus.Matched(queen, 0),
            matches.matchFor(track("Bohemian Rhapsody - Remastered 2011", "Queen")),
        )
    }

    @Test
    fun mediumConfidenceIsOnlySuggested() = runTest {
        val shortVersion = chart("a", "Bohemian Rhapsody", "Queen", durationMs = 240_000)
        charts.saveScanned(shortVersion, 1)

        val status = matches.matchFor(track("Bohemian Rhapsody", "Queen", durationMs = 355_000))

        assertEquals(shortVersion, (status as MatchStatus.Suggested).chart)
    }

    @Test
    fun unrelatedTrackHasNoMatch() = runTest {
        charts.saveScanned(chart("a", "Hurt", "Nine Inch Nails"), 1)

        assertEquals(MatchStatus.NoMatch, matches.matchFor(track("Smells Like Teen Spirit", "Nirvana")))
    }

    @Test
    fun savedChoiceWinsAndKeepsItsOffset() = runTest {
        val queen = chart("a", "Bohemian Rhapsody", "Queen")
        val other = chart("b", "Outra", "Banda")
        charts.saveScanned(queen, 1)
        charts.saveScanned(other, 1)
        db.trackMatchDao().upsert(TrackMatchEntity("spotify:track:x", "b", 1f, isManual = true, trackOffsetMs = 25))

        assertEquals(MatchStatus.Matched(other, 25), matches.matchFor(track("Bohemian Rhapsody", "Queen")))
    }

    @Test
    fun savingAChoiceIsAnnouncedAndKeepsTheOffsetOfTheSameChart() = runTest {
        charts.saveScanned(chart("a", "Música", "Banda"), 1)
        db.trackMatchDao().upsert(TrackMatchEntity("spotify:track:x", "a", 0.8f, isManual = false, trackOffsetMs = 40))
        val before = matches.changes.value

        matches.save("spotify:track:x", "a", confidence = 1f, isManual = true)

        assertEquals(before + 1, matches.changes.value)
        assertEquals(40, db.trackMatchDao().get("spotify:track:x")?.trackOffsetMs)
    }

    @Test
    fun removedChartDropsItsSavedMatch() = runTest {
        charts.saveScanned(chart("a", "Bohemian Rhapsody", "Queen"), 1)
        matches.save("spotify:track:x", "a", confidence = 1f, isManual = true)

        charts.removeNotSeenIn(2) // reimportação em que a pasta sumiu

        assertEquals(MatchStatus.NoMatch, matches.matchFor(track("Bohemian Rhapsody", "Queen")))
    }
}
