package com.rodrigomachado.riff.core.chart.match

import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TrackMatcherTest {
    private fun chart(id: String, title: String, artist: String, durationMs: Long? = null) = Chart(
        id = id,
        title = title,
        artist = artist,
        album = null,
        charter = null,
        durationMs = durationMs,
        folderUri = "content://$id",
        format = ChartFormat.CHART,
        difficulties = setOf(Difficulty.EXPERT),
    )

    @Test
    fun exactTitleAndArtistIsAutomatic() {
        val result = TrackMatcher.bestMatch(
            TrackQuery("Bohemian Rhapsody", "Queen", null),
            listOf(chart("a", "Bohemian Rhapsody", "Queen")),
        )
        assertEquals(MatchKind.AUTOMATIC, result?.kind)
        assertEquals(1f, result!!.confidence, 0.0001f)
    }

    @Test
    fun remasteredSpotifyTitleStillMatchesAutomatically() {
        val result = TrackMatcher.bestMatch(
            TrackQuery("Bohemian Rhapsody - Remastered 2011", "Queen", null),
            listOf(chart("a", "Bohemian Rhapsody", "Queen")),
        )
        assertEquals(MatchKind.AUTOMATIC, result?.kind)
    }

    @Test
    fun sameTitleByAnotherArtistIsNeverAutomatic() {
        val result = TrackMatcher.bestMatch(
            TrackQuery("Hurt", "Johnny Cash", null),
            listOf(chart("a", "Hurt", "Nine Inch Nails")),
        )
        assertNotEquals(MatchKind.AUTOMATIC, result?.kind)
    }

    @Test
    fun durationFarOffLowersConfidenceToConfirmation() {
        val result = TrackMatcher.bestMatch(
            TrackQuery("Bohemian Rhapsody", "Queen", 355_000),
            listOf(chart("a", "Bohemian Rhapsody", "Queen", durationMs = 240_000)),
        )
        assertEquals(MatchKind.NEEDS_CONFIRMATION, result?.kind)
        assertEquals(0.9f, result!!.confidence, 0.0001f)
    }

    @Test
    fun durationWithinFiveSecondsIsNotPenalized() {
        val result = TrackMatcher.bestMatch(
            TrackQuery("Bohemian Rhapsody", "Queen", 354_000),
            listOf(chart("a", "Bohemian Rhapsody", "Queen", durationMs = 355_000)),
        )
        assertEquals(1f, result!!.confidence, 0.0001f)
    }

    @Test
    fun unrelatedOrEmptyLibraryHasNoMatch() {
        assertNull(
            TrackMatcher.bestMatch(
                TrackQuery("Garota de Ipanema", "Tom Jobim", null),
                listOf(chart("a", "Master of Puppets", "Metallica")),
            ),
        )
        assertNull(TrackMatcher.bestMatch(TrackQuery("Qualquer", "Banda", null), emptyList()))
    }

    @Test
    fun picksTheBestAmongSeveral() {
        val charts = listOf(
            chart("a", "Bohemian Like You", "The Dandy Warhols"),
            chart("b", "Bohemian Rhapsody", "Queen"),
        )
        assertEquals("b", TrackMatcher.bestMatch(TrackQuery("Bohemian Rhapsody", "Queen", null), charts)?.chart?.id)
    }
}
