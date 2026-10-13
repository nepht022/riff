package com.rodrigomachado.riff.core.separation

import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * A STFT do Demucs (`standalone_spec` / `standalone_ispec` do demucs.onnx, que chamam `torch.stft`): Hann periódica
 * de 4096, passo 1024, `normalized=True`, `center=True` com reflexão, mais o recheio de 1536 amostras que alinha os
 * quadros com o ramo do tempo. Guarda 2048 faixas (a última sai) e [frames] quadros (os 2 de cada ponta saem).
 */
class DemucsStft(private val length: Int) {
    val frames = (length + Demucs.HOP - 1) / Demucs.HOP
    private val padded = frames * Demucs.HOP + 2 * EDGE
    private val window = FloatArray(Demucs.NFFT) { (0.5 - 0.5 * cos(2 * PI * it / Demucs.NFFT)).toFloat() }
    private val fft = FloatFFT_1D(Demucs.NFFT.toLong())
    private val frame = FloatArray(Demucs.NFFT)
    private val plane = Demucs.BINS * frames

    fun forward(signal: FloatArray, out: FloatArray, offset: Int) {
        val x = reflect(reflect(signal, length, EDGE, padded - length - EDGE), padded, CENTER, CENTER)
        for (t in 0 until frames) {
            val start = (t + 2) * Demucs.HOP
            for (i in 0 until Demucs.NFFT) frame[i] = x[start + i] * window[i]
            fft.realForward(frame)
            out[offset + t] = frame[0] * SCALE
            out[offset + plane + t] = 0f
            for (f in 1 until Demucs.BINS) {
                out[offset + f * frames + t] = frame[2 * f] * SCALE
                out[offset + plane + f * frames + t] = frame[2 * f + 1] * SCALE
            }
        }
    }

    fun inverse(spec: FloatArray, offset: Int, out: FloatArray) {
        val total = Demucs.NFFT + Demucs.HOP * (frames + 3)
        val y = FloatArray(total)
        val envelope = FloatArray(total)
        for (t in 0 until frames + 4) {
            val start = t * Demucs.HOP
            if (t in 2 until frames + 2) addFrame(spec, offset, t - 2, y, start)
            for (i in 0 until Demucs.NFFT) envelope[start + i] += window[i] * window[i]
        }
        for (k in 0 until length) {
            val p = CENTER + EDGE + k
            out[k] = if (envelope[p] > MIN_ENVELOPE) y[p] / envelope[p] else y[p]
        }
    }

    private fun addFrame(spec: FloatArray, offset: Int, column: Int, y: FloatArray, start: Int) {
        frame[0] = spec[offset + column] * UNSCALE
        frame[1] = 0f // a faixa de Nyquist (a 2049ª) é zero
        for (f in 1 until Demucs.BINS) {
            frame[2 * f] = spec[offset + f * frames + column] * UNSCALE
            frame[2 * f + 1] = spec[offset + plane + f * frames + column] * UNSCALE
        }
        fft.realInverse(frame, true)
        for (i in 0 until Demucs.NFFT) y[start + i] += frame[i] * window[i]
    }

    private companion object {
        const val EDGE = Demucs.HOP / 2 * 3
        const val CENTER = Demucs.NFFT / 2
        const val MIN_ENVELOPE = 1e-11f
        val SCALE = 1f / sqrt(Demucs.NFFT.toFloat())
        val UNSCALE = sqrt(Demucs.NFFT.toFloat())

        /** `F.pad(mode="reflect")`: espelha sem repetir a amostra da borda. */
        fun reflect(x: FloatArray, n: Int, left: Int, right: Int) = FloatArray(n + left + right) { i ->
            var j = i - left
            if (j < 0) j = -j
            if (j >= n) j = 2 * (n - 1) - j
            x[j]
        }
    }
}
