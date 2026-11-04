package com.rodrigomachado.riff.core.separation

import kotlinx.coroutines.delay

/** Pausa entre trechos enquanto o celular está quente ([hot]) e volta quando esfria ([cool]). */
class ThermalGate(private val hot: () -> Boolean, private val cool: () -> Boolean, private val pollMs: Long = 2000) {
    suspend fun awaitCool(onPause: (Boolean) -> Unit) {
        if (!hot()) return
        onPause(true)
        while (!cool()) delay(pollMs)
        onPause(false)
    }
}
