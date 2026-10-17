package com.rodrigomachado.riff.core.model

/** Idioma das telas do jogo. [tag] é o código BCP 47 que o Android usa; Português é o padrão. */
enum class AppLanguage(val tag: String) {
    PORTUGUESE("pt-BR"),
    ENGLISH("en"),
    ;

    companion object {
        /** O idioma de um código ("en-GB" é inglês); um que o jogo não tem vira Português. */
        fun fromTag(tag: String?): AppLanguage {
            val language = tag?.substringBefore('-')?.lowercase()
            return entries.firstOrNull { it.tag.substringBefore('-') == language } ?: PORTUGUESE
        }
    }
}
