package com.rodrigomachado.riff.feature.gameplay

import android.graphics.Bitmap
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartArtDecoderTest {
    @Test
    fun aBigImageIsDecodedSmall() {
        val big = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { big.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

        val image = requireNotNull(ChartArtDecoder.decode(bytes, maxSide = 1280))

        assertTrue("${image.width}x${image.height}", image.width <= 2560 && image.height <= 2560)
        assertTrue("${image.width}x${image.height}", image.width < 4000)
    }

    @Test
    fun aSmallImageKeepsItsSize() {
        val small = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

        val image = requireNotNull(ChartArtDecoder.decode(bytes))

        assertTrue(image.width == 300 && image.height == 200)
    }

    @Test
    fun garbageIsIgnored() {
        assertNull(ChartArtDecoder.decode(byteArrayOf(1, 2, 3)))
        assertNull(ChartArtDecoder.decode(ByteArray(0)))
    }
}
