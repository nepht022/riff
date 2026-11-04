package com.rodrigomachado.riff.core.separation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.minutes

/** O port inteiro com o modelo de verdade contra o Demucs original (scripts/separacao_referencia.py). */
class GuitarSeparatorGoldenTest {
    @Test
    fun guitarAndRestMatchTheOriginalDemucs() = runTest(timeout = 5.minutes) {
        val mix = Fixtures.read("mix")
        val n = mix.size / 2
        val sink = CollectingSink()

        OrtDemucsModel(Fixtures.modelBytes()).use { model ->
            GuitarSeparator(model).separate(ArraySong(mix.copyOfRange(0, n), mix.copyOfRange(n, 2 * n)), sink)
        }

        for ((name, values) in listOf("guitar" to sink.guitar, "backing" to sink.backing)) {
            val expected = Fixtures.read(name)
            for (c in 0..1) {
                val got = sink.channel(values, c)
                val corr = correlation(got, expected.copyOfRange(c * n, (c + 1) * n))
                assertTrue("$name canal $c: correlação $corr", corr > 0.99)
            }
        }
    }

    private fun correlation(a: FloatArray, b: FloatArray): Double {
        val ma = a.average()
        val mb = b.average()
        var sab = 0.0
        var saa = 0.0
        var sbb = 0.0
        for (i in a.indices) {
            sab += (a[i] - ma) * (b[i] - mb)
            saa += (a[i] - ma) * (a[i] - ma)
            sbb += (b[i] - mb) * (b[i] - mb)
        }
        return sab / sqrt(saa * sbb)
    }
}
