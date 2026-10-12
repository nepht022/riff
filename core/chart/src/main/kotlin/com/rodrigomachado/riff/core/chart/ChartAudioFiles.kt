package com.rodrigomachado.riff.core.chart

/** Nomes dos arquivos de áudio do Clone Hero e a regra da guitarra separada. */
object ChartAudioFiles {
    val EXTENSIONS = listOf("ogg", "opus", "mp3", "wav")

    /** As faixas do Clone Hero, menos a guitarra; `song` primeiro. */
    val BACKING_NAMES = listOf(
        "song", "rhythm", "bass", "drums", "drums_1", "drums_2", "drums_3", "drums_4",
        "vocals", "vocals_1", "vocals_2", "keys", "crowd",
    )

    /** O nome de [names] que é `<base>.<extensão de áudio>`, sem diferenciar maiúsculas. */
    fun find(names: Collection<String>, base: String): String? = EXTENSIONS.firstNotNullOfOrNull { extension ->
        names.firstOrNull { it.equals("$base.$extension", ignoreCase = true) }
    }

    fun hasSeparateGuitar(names: Collection<String>): Boolean =
        find(names, "guitar") != null && BACKING_NAMES.any { find(names, it) != null }
}
