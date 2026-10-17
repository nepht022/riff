package com.rodrigomachado.riff.core.data.queue

import com.rodrigomachado.riff.core.chart.match.TrackMatcher
import com.rodrigomachado.riff.core.chart.match.TrackQuery
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueChartPickerTest {
    private val spirit = TrackQuery("Smells Like Teen Spirit", "Nirvana", 301_000)
    private val all = setOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD, Difficulty.EXPERT)

    private fun chart(
        md5: String,
        name: String = "Smells Like Teen Spirit",
        artist: String = "Nirvana",
        durationMs: Long? = 301_000,
        guitar: Set<Difficulty> = all,
    ) = EncoreChart(md5, name, artist, "Charter", durationMs, guitar, hasVideoBackground = false)

    private val encore = FakeEncoreSearch()
    private val picker = QueueChartPicker(encore)

    @Test
    fun theSearchIsArtistAndTitle() = runTest {
        picker.pick(spirit)

        assertEquals(listOf("Nirvana Smells Like Teen Spirit"), encore.queries)
    }

    @Test
    fun onlyChartsAboveTheAutomaticThresholdAreKept() = runTest {
        val far = chart("far", durationMs = 200_000, artist = "Nirvana Tribute") // título igual, artista e duração não
        assertTrue(TrackMatcher.score(spirit, far.asChart()) < TrackMatcher.AUTOMATIC_THRESHOLD)
        encore.results = listOf(far)

        assertNull(picker.pick(spirit))
    }

    @Test
    fun aCoverWithTheSameTitleByAnotherArtistIsNotTaken() = runTest {
        encore.results = listOf(chart("cover", artist = "Weird Al Yankovic"))

        assertNull(picker.pick(spirit))
    }

    @Test
    fun originalsComeBeforeVersions() = runTest {
        val live = chart("live", name = "Smells Like Teen Spirit (Live)")
        val original = chart("original", artist = "Nirvana (band)")
        val liveScore = TrackMatcher.score(spirit, live.asChart())
        val originalScore = TrackMatcher.score(spirit, original.asChart())
        assertTrue("$liveScore > $originalScore >= limiar", liveScore > originalScore)
        assertTrue(originalScore >= TrackMatcher.AUTOMATIC_THRESHOLD)
        encore.results = listOf(live, original)

        assertEquals("original", picker.pick(spirit)?.md5)
    }

    @Test
    fun aVersionIsAcceptedOnlyWithoutAnOriginal() = runTest {
        encore.results = listOf(chart("live", name = "Smells Like Teen Spirit (Live)"))

        assertEquals("live", picker.pick(spirit)?.md5)
    }

    @Test
    fun chartsWithoutGuitarAreIgnored() = runTest {
        encore.results = listOf(chart("drums", guitar = emptySet()))

        assertNull(picker.pick(spirit))
    }

    @Test
    fun tieBreakPrefersSeparateGuitarThenMoreDifficulties() = runTest {
        encore.results = listOf(chart("plain"), chart("separate"))
        encore.separate = setOf("separate")
        assertEquals("separate", picker.pick(spirit)?.md5)

        encore.results = listOf(chart("two", guitar = setOf(Difficulty.HARD, Difficulty.EXPERT)), chart("four"))
        encore.separate = emptySet()
        assertEquals("four", picker.pick(spirit)?.md5)
    }

    @Test
    fun atMostThreeChartsArePeeked() = runTest {
        encore.results = (1..5).map { chart("c$it") }

        picker.pick(spirit)

        assertEquals(3, encore.peeks)
    }

    @Test
    fun aFailedSearchGivesNothing() = runTest {
        encore.failure = java.io.IOException("Encore fora do ar")

        assertNull(picker.pick(spirit))
    }
}

class FakeEncoreSearch : EncoreSearch {
    var results: List<EncoreChart> = emptyList()
    var separate: Set<String> = emptySet()
    var failure: Exception? = null
    val queries = mutableListOf<String>()
    var peeks = 0

    override suspend fun search(query: String): List<EncoreChart> {
        queries += query
        failure?.let { throw it }
        return results
    }

    override suspend fun hasSeparateGuitar(chart: EncoreChart): Boolean? {
        peeks++
        return chart.md5 in separate
    }
}
