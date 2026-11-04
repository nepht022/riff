package com.rodrigomachado.riff.core.separation

import org.junit.Assume.assumeFalse
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** As referências geradas por `scripts/separacao_referencia.py` e o modelo de verdade. */
object Fixtures {
    /** O asset do APK, lido direto do projeto (os testes rodam na pasta do módulo, no Android e no PC). */
    private val model = File("src/main/assets/${Demucs.MODEL_ASSET}")

    fun read(name: String): FloatArray {
        val bytes = checkNotNull(javaClass.getResource("/separacao/$name.f32")).readBytes()
        val floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(floats.remaining()).also(floats::get)
    }

    /**
     * O modelo inteiro em memória (o ONNX Runtime de desktop dos testes não abre de um ByteBuffer). Sem o arquivo
     * do Git LFS baixado (só o ponteiro), o teste é pulado.
     */
    fun modelBytes(): ByteArray {
        assumeFalse(
            "${model.path} é só o ponteiro do Git LFS (rode `git lfs pull`): teste com o modelo pulado",
            model.length() < LFS_POINTER_MAX_BYTES,
        )
        return model.readBytes()
    }

    private const val LFS_POINTER_MAX_BYTES = 1024L
}
