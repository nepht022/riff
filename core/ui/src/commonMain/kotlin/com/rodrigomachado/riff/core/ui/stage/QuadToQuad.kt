package com.rodrigomachado.riff.core.ui.stage

/**
 * Homografia (3×3, por linhas) que leva o quadrilátero [src] ao [dst], cada um com 4 cantos (x, y alternados):
 * exata para um plano visto em perspectiva, como o `setPolyToPoly` do Android com 4 pontos. Normalizada com o
 * último valor 1. Ponto (x, y) vai a ((m0·x + m1·y + m2) / w, (m3·x + m4·y + m5) / w), w = m6·x + m7·y + m8.
 */
fun quadToQuad(src: FloatArray, dst: FloatArray): FloatArray {
    require(src.size >= QUAD && dst.size >= QUAD) { "4 cantos (8 números) em cada quadrilátero" }
    // Quadrado unitário → origem, invertido, e quadrado unitário → destino.
    val m = times(squareToQuad(dst), adjugate(squareToQuad(src)))
    return FloatArray(m.size) { (m[it] / m[LAST]).toFloat() }
}

private const val QUAD = 8
private const val LAST = 8

/** Quadrado (0,0) (1,0) (1,1) (0,1) → os 4 cantos de [q], na mesma ordem (Heckbert). */
private fun squareToQuad(q: FloatArray): DoubleArray {
    val x0 = q[0].toDouble()
    val y0 = q[1].toDouble()
    val x1 = q[2].toDouble()
    val y1 = q[3].toDouble()
    val x2 = q[4].toDouble()
    val y2 = q[5].toDouble()
    val x3 = q[6].toDouble()
    val y3 = q[7].toDouble()
    val dx1 = x1 - x2
    val dx2 = x3 - x2
    val dy1 = y1 - y2
    val dy2 = y3 - y2
    val sx = x0 - x1 + x2 - x3
    val sy = y0 - y1 + y2 - y3
    val den = dx1 * dy2 - dx2 * dy1
    val g = (sx * dy2 - dx2 * sy) / den
    val h = (dx1 * sy - sx * dy1) / den
    return doubleArrayOf(
        x1 - x0 + g * x1, x3 - x0 + h * x3, x0,
        y1 - y0 + g * y1, y3 - y0 + h * y3, y0,
        g, h, 1.0,
    )
}

/** Inversa a menos de escala (a escala some na homografia). */
private fun adjugate(m: DoubleArray): DoubleArray = doubleArrayOf(
    m[4] * m[8] - m[5] * m[7], m[2] * m[7] - m[1] * m[8], m[1] * m[5] - m[2] * m[4],
    m[5] * m[6] - m[3] * m[8], m[0] * m[8] - m[2] * m[6], m[2] * m[3] - m[0] * m[5],
    m[3] * m[7] - m[4] * m[6], m[1] * m[6] - m[0] * m[7], m[0] * m[4] - m[1] * m[3],
)

private fun times(a: DoubleArray, b: DoubleArray): DoubleArray = DoubleArray(9) { i ->
    val row = i / 3
    val col = i % 3
    (0 until 3).sumOf { k -> a[row * 3 + k] * b[k * 3 + col] }
}
