package com.rodrigomachado.riff.core.audio

/** Aviso da thread de áudio (no celular, o logcat). */
internal expect fun audioLog(message: String, error: Throwable?)

/** Relógio monotônico em ms (não anda para trás nem salta com o relógio do sistema). */
internal expect fun uptimeMs(): Long

/** Prioridade de áudio para a thread que alimenta a saída, para o sistema não atrasá-la. */
internal expect fun raiseAudioThreadPriority()
