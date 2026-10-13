package com.rodrigomachado.riff.core.separation

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** As referências geradas por `scripts/separacao_referencia.py` e o modelo de verdade. */
object Fixtures {
    fun read(name: String): FloatArray {
        val bytes = checkNotNull(javaClass.getResource("/separacao/$name.f32")).readBytes()
        val floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(floats.remaining()).also(floats::get)
    }

    /** O modelo inteiro em memória (o ONNX Runtime de desktop dos testes não abre de um ByteBuffer). */
    fun modelBytes(): ByteArray = File("src/main/assets/${Demucs.MODEL_ASSET}").readBytes()
}
