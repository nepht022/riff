package com.rodrigomachado.riff.core.separation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/** O modelo no ONNX Runtime (CPU). */
class OrtDemucsModel internal constructor(private val session: OrtSession) : DemucsModel {
    private val env = OrtEnvironment.getEnvironment()

    /**
     * Nos testes e no PC: o ONNX Runtime de desktop compatível com o Java do Android Studio (1.18) não abre de um
     * ByteBuffer (o do celular abre: `OrtDemucsModel(ByteBuffer)` no Android).
     */
    constructor(model: ByteArray, threads: Int = 4, useArena: Boolean = true) :
        this(session(threads, useArena) { env, options -> env.createSession(model, options) })

    override fun run(mix: FloatArray, spec: FloatArray): DemucsOutput =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(mix), MIX_SHAPE).use { mixTensor ->
            OnnxTensor.createTensor(env, FloatBuffer.wrap(spec), SPEC_SHAPE).use { specTensor ->
                session.run(mapOf("mix" to mixTensor, "spec" to specTensor)).use { result ->
                    DemucsOutput(floats(result, "spec_out"), floats(result, "mix_out"))
                }
            }
        }

    private fun floats(result: OrtSession.Result, name: String): FloatArray {
        val buffer = (result.get(name).get() as OnnxTensor).floatBuffer
        return FloatArray(buffer.remaining()).also(buffer::get)
    }

    override fun close() = session.close()

    internal companion object {
        fun session(
            threads: Int,
            useArena: Boolean,
            open: (OrtEnvironment, OrtSession.SessionOptions) -> OrtSession,
        ): OrtSession = OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(threads)
            options.setInterOpNumThreads(1)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // Sem a arena o pico de memória cai (~2,8 → ~1,8 GB no S25) e a separação fica ~40 % mais lenta.
            options.setCPUArenaAllocator(useArena)
            options.setMemoryPatternOptimization(useArena)
            open(OrtEnvironment.getEnvironment(), options)
        }

        private val MIX_SHAPE = longArrayOf(1, 2, Demucs.SEGMENT.toLong())
        private val SPEC_SHAPE = longArrayOf(1, 4, Demucs.BINS.toLong(), Demucs.FRAMES.toLong())
    }
}
