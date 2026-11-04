package com.rodrigomachado.riff.core.separation

/** Log da separação: aviso com [error], informação sem. No celular vai para o Logcat. */
internal expect fun separationLog(tag: String, message: String, error: Throwable? = null)
