package com.rodrigomachado.riff.core.data.game

import com.rodrigomachado.riff.core.data.importer.FakeChartRepository
import com.rodrigomachado.riff.core.data.importer.FakeFolderSource
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun withoutBackgroundTheAlbumCoverIsUsed() = runTest {
        source.file("raiz/M/album.jpeg", "capa")
        source.file("raiz/M/album.txt", "não é imagem")
        chart("raiz/M")

        assertEquals("capa", loader().load("id")?.decodeToString())
    }

    @Test
    fun noImageOrNoChartGivesNothing() = runTest {
        source.file("raiz/M/song.ogg", "música")
        chart("raiz/M")

        assertNull(loader().load("id"))
        assertNull(loader().load("outro"))
    }

    @Test
    fun anImageTooLargeIsSkipped() = runTest {
        source.file("raiz/M/background.jpg", ByteArray(9 * 1024 * 1024))
        chart("raiz/M")

        assertNull(loader().load("id"))
    }

    @Test
    fun aFolderThatCannotBeReadGivesNothing() = runTest {
        source.file("raiz/M/background.jpg", "fundo")
        source.revoked += "raiz/M"
        chart("raiz/M")

        assertNull(loader().load("id"))
    }
}
