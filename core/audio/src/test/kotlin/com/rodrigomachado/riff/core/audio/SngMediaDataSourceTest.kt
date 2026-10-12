package com.rodrigomachado.riff.core.audio

import com.rodrigomachado.riff.core.chart.sng.ByteArrayRandomRead
import com.rodrigomachado.riff.core.chart.sng.SngRef
import com.rodrigomachado.riff.core.chart.sng.SngWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SngMediaDataSourceTest {
    private val audio = ByteArray(5_000) { (it * 7 % 256).toByte() }
    private val sng = SngWriter.write(mapOf("name" to "M"), mapOf("notes.chart" to ByteArray(3), "song.opus" to audio))

    @Test
    fun theAudioInsideThePackageIsReadAtAnyPositionLikeAFile() {
        val source = checkNotNull(audioDataSource(SngRef.entryUri("x.sng", "song.opus")) { ByteArrayRandomRead(sng) })
        val buffer = ByteArray(100)

        assertEquals(5_000L, source.size)
        // O extrator lê fora de ordem (reposicionar ao voltar 0,5 s).
        assertEquals(100, source.readAt(4_000, buffer, 0, 100))
        assertArrayEquals(audio.copyOfRange(4_000, 4_100), buffer)
        assertEquals(100, source.readAt(10, buffer, 0, 100))
        assertArrayEquals(audio.copyOfRange(10, 110), buffer)
        assertEquals(-1, source.readAt(5_000, buffer, 0, 100))
        source.close()
    }

    @Test
    fun aPlainUriHasNoPackageDataSource() {
        assertNull(audioDataSource("content://x/song.ogg") { error("não deveria abrir") })
    }
}
