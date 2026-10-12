package com.rodrigomachado.riff.core.chart.match

import java.text.Normalizer

/** Normaliza título e artista para comparar a faixa do Spotify com o mapa (spec §5). */
object TextNormalizer {
    private const val QUALIFIERS = "remaster(?:ed)?|live|ao vivo|version|versão|edit|mono|stereo"
    private val FEATURING_IN_BRACKETS = Regex(
        """\s*[(\[]\s*(?:feat\.?|ft\.?|featuring)\s[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val FEATURING_BARE = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s.*$""", RegexOption.IGNORE_CASE)
    private val QUALIFIER_IN_BRACKETS = Regex(
        """\s*[(\[][^)\]]*\b(?:$QUALIFIERS)\b[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val QUALIFIER_AFTER_DASH = Regex("""\s+-\s+.*\b(?:$QUALIFIERS)\b.*$""", RegexOption.IGNORE_CASE)
    private val DIACRITICS = Regex("\\p{Mn}+")

    // Letras e números de qualquer escrita (japonês, cirílico...), para nomes não latinos não sumirem.
    // Letras e números de qualquer escrita (japonês, cirílico...), para nomes não latinos não sumirem.
    private val NOT_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
    private val APOSTROPHES = Regex("['’‘ʼ]")

    fun normalize(text: String): String {
        val withoutExtras = text
            .replace(FEATURING_IN_BRACKETS, " ")
            .replace(FEATURING_BARE, "")
            .replace(QUALIFIER_IN_BRACKETS, " ")
            .replace(QUALIFIER_AFTER_DASH, "")
        val withoutAccents = Normalizer.normalize(withoutExtras, Normalizer.Form.NFD).replace(DIACRITICS, "")
        return withoutAccents
            .lowercase()
            .replace("&", " and ")
            .replace(APOSTROPHES, "") // reto e tipográficos: "Don’t" casa com "Don't"
            .replace(NOT_ALPHANUMERIC, " ")
            .trim()
    }
}
