package com.rodrigomachado.riff.feature.gameplay

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartArtDecoderDesktopTest {
    private fun png(width: Int, height: Int): ByteArray {
        val image = Surface.makeRasterN32Premul(width, height).makeImageSnapshot()
        return requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes
    }

    @Test
    fun aBigImageIsDecodedWithinTheMaxSide() {
        val image = requireNotNull(ChartArtDecoder.decode(png(4000, 3000), maxSide = 1280))

        assertEquals(1280, image.width)
        assertEquals(960, image.height)
    }

    @Test
    fun aTallImageIsLimitedByItsHeight() {
        val image = requireNotNull(ChartArtDecoder.decode(png(1000, 2000)))

        assertEquals(640, image.width)
        assertEquals(1280, image.height)
    }

    @Test
    fun aSmallImageKeepsItsSize() {
        val image = requireNotNull(ChartArtDecoder.decode(png(300, 200)))

        assertEquals(300, image.width)
        assertEquals(200, image.height)
    }

    @Test
    fun garbageIsIgnored() {
        assertNull(ChartArtDecoder.decode(byteArrayOf(1, 2, 3)))
        assertNull(ChartArtDecoder.decode(ByteArray(0)))
    }
}
