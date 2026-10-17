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

    /** Sem duração no Encore, título e artista quase iguais não bastam para baixar sozinho. */
    @Test
    fun aChartWithoutDurationIsNotTaken() = runTest {
        encore.results = listOf(chart("sem-duracao", durationMs = null))

        assertNull(picker.pick(spirit))
    }

    private val rainingBlood = TrackQuery("Raining Blood", "Slayer", 255_000)

    private fun slayer(md5: String, durationMs: Long?, artist: String = "Slayer") =
        chart(md5, name = "Raining Blood", artist = artist, durationMs = durationMs)

    /** O caso do Raining Blood: nenhum com a duração do Spotify; o mais próximo vem como sugestão. */
    @Test
    fun withoutTheSameDurationTheClosestComesAsASuggestion() = runTest {
        encore.results = listOf(slayer("212", 212_264), slayer("264", 264_000), slayer("221", 221_085))

        val pick = picker.choose(rainingBlood)

        assertTrue("$pick", pick is QueuePick.Closest)
        pick as QueuePick.Closest
        assertEquals("264", pick.chart.md5)
        assertTrue(pick.confidence >= TrackMatcher.AUTOMATIC_THRESHOLD)
    }

    @Test
    fun theSameDurationIsStillTheFirstChoice() = runTest {
        encore.results = listOf(slayer("264", 264_000), slayer("igual", 256_000))

        assertEquals(QueuePick.Exact(slayer("igual", 256_000)), picker.choose(rainingBlood))
    }

    @Test
    fun moreThanAMinuteAwayIsNotTaken() = runTest {
        encore.results = listOf(slayer("longa", 316_000), slayer("sem-duracao", null))

        assertEquals(QueuePick.NothingReliable, picker.choose(rainingBlood))
    }

    /** O mais próximo ainda precisa de título e artista: um cover de outra banda não serve. */
    @Test
    fun theClosestStillNeedsTheSameArtist() = runTest {
        encore.results = listOf(slayer("cover", 254_000, artist = "Todd Barriage"), slayer("longe", 212_000, "Havok"))

        assertEquals(QueuePick.NothingReliable, picker.choose(rainingBlood))
    }

    /** O caso do Big Empty: no Encore só o mapa da Harmonix, que o Riff não usa. */
    @Test
    fun onlyAnOfficialChartIsToldApart() = runTest {
        encore.official = listOf(slayer("harmonix", 255_000))
        assertEquals(QueuePick.OnlyOfficial, picker.choose(rainingBlood))

        encore.official = listOf(slayer("outra-banda", 255_000, artist = "Metallica"))
        assertEquals(QueuePick.NothingReliable, picker.choose(rainingBlood))
    }

    @Test
    fun aFailedSearchIsToldApartFromNothingFound() = runTest {
        encore.failure = java.io.IOException("Encore fora do ar")

        assertEquals(QueuePick.SearchFailed, picker.choose(spirit))
    }

    /** A faixa do Spotify é ao vivo: o mapa ao vivo vem antes do de estúdio. */
    @Test
    fun aLiveTrackKeepsTheLiveChartFirst() = runTest {
        val live = TrackQuery("Smells Like Teen Spirit (Live)", "Nirvana", 301_000)
        encore.results = listOf(chart("studio"), chart("live", name = "Smells Like Teen Spirit (Live)"))

        assertEquals("live", picker.pick(live)?.md5)
    }
}

class FakeEncoreSearch : EncoreSearch {
    var results: List<EncoreChart> = emptyList()

    /** Resultados por consulta; sem a consulta aqui, vale [results]. */
    var byQuery: Map<String, List<EncoreChart>> = emptyMap()

    /** Os oficiais (Rock Band, Guitar Hero) que a pesquisa achou e deixou de fora. */
    var official: List<EncoreChart> = emptyList()
    var separate: Set<String> = emptySet()
    var failure: Exception? = null
    val queries = mutableListOf<String>()
    var peeks = 0

    override suspend fun search(query: String): EncoreFound {
        queries += query
        failure?.let { throw it }
        return EncoreFound(byQuery[query] ?: results, official)
    }

    override suspend fun hasSeparateGuitar(chart: EncoreChart): Boolean? {
        peeks++
        return chart.md5 in separate
    }
}
