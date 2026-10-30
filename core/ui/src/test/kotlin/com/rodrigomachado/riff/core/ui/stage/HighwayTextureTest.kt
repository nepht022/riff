package com.rodrigomachado.riff.core.ui.stage

import com.rodrigomachado.riff.core.model.HighwayArt
import com.rodrigomachado.riff.core.model.HighwayBackground
import com.rodrigomachado.riff.core.model.TrackLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HighwayTextureTest {
    @Test
    fun theStageOfTodayAndNoArtNeedNoTexture() {
        assertNull(HighwayTexture.background(HighwayBackground.STAGE))
        assertNull(HighwayTexture.art(HighwayArt.NONE))
    }

    @Test
    fun everyOtherBackgroundIsAnOpaqueTileOfTheRightSize() {
        for (kind in HighwayBackground.entries - HighwayBackground.STAGE) {
            val tile = requireNotNull(HighwayTexture.background(kind)) { kind.name }
            assertEquals(kind.name, HighwayTexture.WIDTH, tile.width)
            assertEquals(kind.name, HighwayTexture.DEPTH, tile.height)
            assertEquals(kind.name, 255, tile.getPixel(tile.width / 2, tile.height / 2) ushr 24)
        }
    }

    @Test
    fun artIsTransparentAroundTheStamps() {
        for (kind in HighwayArt.entries - HighwayArt.NONE) {
            val tile = requireNotNull(HighwayTexture.art(kind)) { kind.name }
            val pixels = IntArray(tile.width * tile.height)
            tile.getPixels(pixels, 0, tile.width, 0, 0, tile.width, tile.height)
            val alphas = pixels.map { it ushr 24 }
            assertTrue(kind.name, alphas.any { it == 0 }) // há área livre
            assertTrue(kind.name, alphas.any { it in 1..120 }) // e estampa clara (não opaca)
            assertTrue(kind.name, alphas.none { it > 120 })
        }
    }

    /** Perto do horizonte o ladrilho encolhe ~4×: sem mipmap, padrões finos cintilam. */
    @Test
    fun tilesHaveMipmapsForTheFarEnd() {
        for (kind in HighwayBackground.entries - HighwayBackground.STAGE) {
            assertTrue(kind.name, HighwayTexture.background(kind)!!.hasMipMap())
        }
        for (kind in HighwayArt.entries - HighwayArt.NONE) {
            assertTrue(kind.name, HighwayTexture.art(kind)!!.hasMipMap())
        }
    }

    /** Gerado antes, fora da tela: o primeiro quadro (ou a lista das Configurações) só pega o pronto. */
    @Test
    fun preparingLeavesTheTilesReady() {
        HighwayTexture.prepareAll()

        assertTrue(HighwayTexture.isPrepared(TrackLook.DEFAULT.copy(background = HighwayBackground.NEON)))
    }

    @Test
    fun tilesAreTheSameEveryTime() {
        val a = HighwayTexture.background(HighwayBackground.LEATHER)!!
        val b = HighwayTexture.background(HighwayBackground.LEATHER)!!
        assertTrue(a.sameAs(b))
    }

    @Test
    fun theTileRepeatsWithoutAVisibleSeam() {
        for (kind in HighwayBackground.entries - HighwayBackground.STAGE) {
            val tile = HighwayTexture.background(kind)!!
            val difference = (0 until tile.width).map { x ->
                channelDistance(tile.getPixel(x, 0), tile.getPixel(x, tile.height - 1))
            }.average()
            // A linha de cima continua a de baixo: sem degrau na emenda.
            assertTrue("${kind.name}: $difference", difference < SEAM_LIMIT)
        }
    }

    private fun channelDistance(a: Int, b: Int): Int =
        (0..2).sumOf { abs(((a shr (it * 8)) and 0xFF) - ((b shr (it * 8)) and 0xFF)) } / 3

    private companion object {
        const val SEAM_LIMIT = 40
    }
}
