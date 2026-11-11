package com.rodrigomachado.riff.core.data.game

import com.rodrigomachado.riff.core.data.importer.FakeChartRepository
import com.rodrigomachado.riff.core.data.importer.FakeFolderSource
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartArtUnavailableException
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartArtLoaderTest {
    private val source = FakeFolderSource()
    private val charts = FakeChartRepository()

    private suspend fun chart(folder: String) = charts.saveScanned(
        Chart("id", "M", "B", null, null, null, folder, ChartFormat.CHART, setOf(Difficulty.EXPERT)),
        scanId = 1,
    )

    private fun TestScope.loader() = FolderChartArtLoader(source, charts, StandardTestDispatcher(testScheduler))

    @Test
    fun theBackgroundWinsOverTheAlbumCover() = runTest {
        source.file("raiz/M/album.jpg", "capa")
        source.file("raiz/M/Background.PNG", "fundo")
        chart("raiz/M")

        assertEquals("fundo", loader().load("id")?.decodeToString())
    }

    /** A miniatura das listas é a capa (quadrada, menor): ela vem antes do fundo; sem ela, o fundo. */
    @Test
    fun forThumbnailsTheAlbumCoverWinsOverTheBackground() = runTest {
        source.file("raiz/M/album.jpg", "capa")
        source.file("raiz/M/background.png", "fundo")
        source.file("raiz/N/background.png", "fundo de N")
        chart("raiz/M")
        charts.saveScanned(
            Chart("n", "N", "B", null, null, null, "raiz/N", ChartFormat.CHART, setOf(Difficulty.EXPERT)),
            scanId = 1,
        )

        assertEquals("capa", loader().load("id", ChartArtUse.THUMB) { it }?.decodeToString())
        assertEquals("fundo de N", loader().load("n", ChartArtUse.THUMB) { it }?.decodeToString())
    }

    @Test
    fun withoutBackgroundTheAlbumCoverIsUsed() = runTest {
        source.file("raiz/M/album.jpeg", "capa")
        source.file("raiz/M/album.txt", "não é imagem")
        chart("raiz/M")

        assertEquals("capa", loader().load("id")?.decodeToString())
    }

    @Test
    fun noImageGivesNothing() = runTest {
        source.file("raiz/M/song.ogg", "música")
        chart("raiz/M")

        assertNull(loader().load("id"))
    }

    /** Mapa fora do banco (ainda vai ser baixado): não deu para procurar, o que é diferente de "sem imagem". */
    @Test
    fun aChartNotInTheLibraryCannotBeLookedUp() = runTest {
        assertTrue(runCatching { loader().load("outro") }.exceptionOrNull() is ChartArtUnavailableException)
    }

    @Test
    fun anImageTooLargeIsSkipped() = runTest {
        source.file("raiz/M/background.jpg", ByteArray(9 * 1024 * 1024))
        chart("raiz/M")

        assertNull(loader().load("id"))
    }

    @Test
    fun aBackgroundTooLargeFallsBackToTheAlbumCover() = runTest {
        source.file("raiz/M/background.jpg", ByteArray(9 * 1024 * 1024))
        source.file("raiz/M/album.png", "capa")
        chart("raiz/M")

        assertEquals("capa", loader().load("id")?.decodeToString())
    }

    @Test
    fun aBackgroundThatDoesNotDecodeFallsBackToTheAlbumCover() = runTest {
        source.file("raiz/M/background.jpg", "corrompido")
        source.file("raiz/M/album.png", "capa")
        chart("raiz/M")

        val decoded = loader().load("id") { bytes -> bytes.decodeToString().takeIf { it != "corrompido" } }

        assertEquals("capa", decoded)
    }

    @Test
    fun aFolderThatCannotBeReadCannotBeLookedUp() = runTest {
        source.file("raiz/M/background.jpg", "fundo")
        source.revoked += "raiz/M"
        chart("raiz/M")

        assertTrue(runCatching { loader().load("id") }.exceptionOrNull() is ChartArtUnavailableException)
    }
}
