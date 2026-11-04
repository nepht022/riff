package com.rodrigomachado.riff.core.separation

import java.nio.ByteBuffer

/** No app: [model] é o asset mapeado em memória (buffer direto), sem cópia. */
fun OrtDemucsModel(model: ByteBuffer, threads: Int = 4, useArena: Boolean = true): OrtDemucsModel =
    OrtDemucsModel(OrtDemucsModel.session(threads, useArena) { env, options -> env.createSession(model, options) })
