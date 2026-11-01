package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.model.AppLanguage

/** O idioma escolhido nas Configurações. Trocar muda o app inteiro na hora (a tela aberta é refeita). */
interface AppLanguageStore {
    val current: AppLanguage

    fun set(language: AppLanguage)
}
