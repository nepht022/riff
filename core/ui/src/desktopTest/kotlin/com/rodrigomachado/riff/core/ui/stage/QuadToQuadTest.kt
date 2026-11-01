package com.rodrigomachado.riff.core.ui.stage

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class QuadToQuadTest {
    /** Faixa do ladrilho (como em `StageCache`) e o trapézio da pista, mais estreito no fundo. */
    private val src = floatArrayOf(0f, -100f, 256f, -100f, 256f, -900f, 0f, -900f)
    private val dst = floatArrayOf(40f, 1200f, 680f, 1200f, 470f, 300f, 250f, 300f)

    @Test
    fun theFourCornersGoToTheFourCorners() {
        val m = quadToQuad(src, dst)

        for (i in 0 until 4) {
            val (x, y) = map(m, src[i * 2], src[i * 2 + 1])
            assertEquals("x do canto $i", dst[i * 2], x, TOLERANCE)
            assertEquals("y do canto $i", dst[i * 2 + 1], y, TOLERANCE)
        }
    }

    /** Em perspectiva, o meio do retângulo cai no cruzamento das diagonais do trapézio, não no meio da altura. */
    @Test
    fun theCenterGoesToWhereTheDiagonalsCross() {
        val m = quadToQuad(src, dst)

        val (x, y) = map(m, (src[0] + src[4]) / 2f, (src[1] + src[5]) / 2f)

        val (cx, cy) = diagonalsCross(dst)
        assertEquals(cx, x, TOLERANCE)
        assertEquals(cy, y, TOLERANCE)
        assertEquals(false, abs(y - (dst[1] + dst[5]) / 2f) < 1f) // a perspectiva aparece
    }

    @Test
    fun theSameQuadIsTheIdentity() {
        val m = quadToQuad(src, src)

        val identity = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        for (i in 0 until 9) assertEquals("valor $i", identity[i], m[i] / m[8], 1e-5f)
    }

    private fun map(m: FloatArray, x: Float, y: Float): Pair<Float, Float> {
        val w = m[6] * x + m[7] * y + m[8]
        return (m[0] * x + m[1] * y + m[2]) / w to (m[3] * x + m[4] * y + m[5]) / w
    }

    /** Cruzamento da diagonal 0→2 com a 1→3. */
    private fun diagonalsCross(q: FloatArray): Pair<Float, Float> {
        val x0 = q[0]
        val y0 = q[1]
        val x1 = q[2]
        val y1 = q[3]
        val x2 = q[4]
        val y2 = q[5]
        val x3 = q[6]
        val y3 = q[7]
        val d = (x0 - x2) * (y1 - y3) - (y0 - y2) * (x1 - x3)
        val a = x0 * y2 - y0 * x2
        val b = x1 * y3 - y1 * x3
        return (a * (x1 - x3) - (x0 - x2) * b) / d to (a * (y1 - y3) - (y0 - y2) * b) / d
    }

    private companion object {
        const val TOLERANCE = 0.01f
    }
}
