package com.rodrigomachado.riff.core.separation

import java.io.File

interface ClosableStemSink : StemSink {
    /** Fecha os arquivos; sem isto eles ficam inválidos. */
    fun finish()

    /** Desiste (falha ou cancelamento): libera os codificadores sem fechar os arquivos. */
    fun abort()
}

fun interface StemSinkFactory {
    fun create(dir: File): ClosableStemSink
}
