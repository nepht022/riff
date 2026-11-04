package com.rodrigomachado.riff.core.separation

import java.io.Closeable

/** O htdemucs_6s exportado com trechos de 4 s (ver `scripts/exportar_modelo_ia.py`). */
object Demucs {
    const val SAMPLE_RATE = 44_100
    const val SEGMENT = 176_400
    const val NFFT = 4096
    const val HOP = 1024
    const val BINS = 2048
    const val FRAMES = 173
    const val SOURCES = 6

    /** Ordem do htdemucs_6s: bateria, baixo, outros, voz, guitarra, piano. */
    const val GUITAR = 4
    const val MODEL_ASSET = "htdemucs_6s_seg4.onnx"
}

/** Saídas de um trecho: espectro `[6, 4, 2048, 173]` (CaC) e onda `[6, 2, 176400]`, achatados. */
class DemucsOutput(val spec: FloatArray, val mix: FloatArray)

/** Uma passada do modelo num trecho: onda `[2, 176400]` e espectro `[4, 2048, 173]` (CaC), achatados. */
interface DemucsModel : Closeable {
    fun run(mix: FloatArray, spec: FloatArray): DemucsOutput
}

fun interface DemucsModelFactory {
    fun create(useArena: Boolean): DemucsModel
}
