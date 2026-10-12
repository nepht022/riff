package com.rodrigomachado.riff.core.data.folder

import com.rodrigomachado.riff.core.chart.sng.ByteArrayRandomRead
import com.rodrigomachado.riff.core.chart.sng.SngRef
import com.rodrigomachado.riff.core.chart.sng.SngWriter
import com.rodrigomachado.riff.core.data.importer.FakeFolderSource
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SngFolderSourceTest {
    private val tree = FakeFolderSource()
    private val audio = ByteArray(300) { it.toByte() }
    private val source = SngFolderSource(tree) { ByteArrayRandomRead(tree.bytes(it)) }

    init {
        tree.file(
            "raiz/a.sng",
            SngWriter.write(mapOf("name" to "A"), mapOf("notes.chart" to ByteArray(5), "song.opus" to audio)),
        )
        tree.file("raiz/b/notes.chart", "comum")
    }

    @Test
    fun aPackageIsListedLikeAFolderWithItsFilesAndSizes() = runTest {
        val children = source.listChildren(SngRef.packageUri("raiz/a.sng"))

        // Os arquivos do índice e o song.ini montado dos metadados (o .sng não traz um).
        assertEquals(listOf("notes.chart", "song.opus", "song.ini"), children.map { it.name })
        assertEquals(listOf(5L, 300L), children.take(2).map { it.sizeBytes })
        assertEquals("[song]\nname = A", String(source.read(children[2], maxBytes = 1_000)))
        assertEquals(SngRef.entryUri("raiz/a.sng", "song.opus"), children[1].uri)
        assertArrayEquals(audio, source.read(children[1], maxBytes = 1_000))
    }

    @Test
    fun aFileInsideAPackageRespectsTheSizeLimit() = runTest {
        val song = source.listChildren(SngRef.packageUri("raiz/a.sng"))[1]
        assertThrows(FileTooLargeException::class.java) { runBlocking { source.read(song, maxBytes = 10) } }
    }

    @Test
    fun ordinaryFoldersAndFilesGoToTheUnderlyingSource() = runTest {
        val file = source.listChildren("raiz/b").single()
        assertEquals("comum", String(source.read(file, maxBytes = 100)))
    }
}
