package com.rodrigomachado.riff.desktop

import com.rodrigomachado.riff.core.data.AppLanguageStore
import com.rodrigomachado.riff.core.model.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * O idioma do jogo no PC, guardado em [file] (o código do idioma): sem arquivo, ou ilegível, Português (como no
 * celular, mesmo num Windows em inglês). Escolher aplica na hora: os textos leem o `Locale` padrão, e [language] avisa
 * o app para refazer as telas.
 */
class DesktopAppLanguageStore(private val file: File) : AppLanguageStore {
    private val chosen = MutableStateFlow(read())
    val language: StateFlow<AppLanguage> = chosen.asStateFlow()

    override val current: AppLanguage get() = chosen.value

    override fun set(language: AppLanguage) {
        try {
            file.writeText(language.tag)
        } catch (_: IOException) {
            // Sem gravar, o idioma vale até fechar o jogo.
        }
        chosen.value = language
        apply()
    }

    /** Aplica o idioma escolhido aos textos (na abertura, antes da primeira tela). */
    fun apply() = Locale.setDefault(Locale.forLanguageTag(current.tag))

    private fun read(): AppLanguage = try {
        AppLanguage.fromTag(file.takeIf { it.exists() }?.readText()?.trim())
    } catch (_: IOException) {
        AppLanguage.PORTUGUESE
    }
}

/** O arquivo do idioma na pasta de dados. */
internal const val LANGUAGE_FILE = "language.txt"
