package com.rodrigomachado.riff.feature.report

/** O que vai junto do relato quando a pessoa deixa "Incluir informações do aparelho" ligado. */
fun interface DeviceInfoSource {
    fun read(): DeviceInfo
}
