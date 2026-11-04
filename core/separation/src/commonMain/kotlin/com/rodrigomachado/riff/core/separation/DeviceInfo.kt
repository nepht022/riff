package com.rodrigomachado.riff.core.separation

/** Memória total do aparelho (para a arena e o aviso) e espaço livre onde as separações ficam. */
interface DeviceInfo {
    val totalRamBytes: Long

    fun freeBytes(): Long
}
