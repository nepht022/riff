package com.rodrigomachado.riff.core.encore

/**
 * Título de uma versão (cover, remix, ao vivo, medley…) em vez da gravação original. Pelo título do Encore, palavra
 * inteira: "Livewire" e "Alive" não são versões; "Remastered" é a original.
 */
object VersionTitles {
    private val TERMS = Regex(
        """\b(cover|remix|live|ao vivo|medley|mashup|acoustic)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun isVersion(title: String): Boolean = TERMS.containsMatchIn(title)
}
