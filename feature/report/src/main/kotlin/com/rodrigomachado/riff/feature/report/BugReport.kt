package com.rodrigomachado.riff.feature.report

import androidx.annotation.StringRes
import org.json.JSONObject

/** O que a pessoa está relatando; [code] é como aparece no email. */
enum class ReportKind(val code: String) {
    PROBLEM("problema"),
    SUGGESTION("sugestao"),
    OTHER("outro"),
}

/**
 * Categoria para separar os relatos no email. [label] é o nome que aparece no email (em português); [nameRes], o
 * da tela, no idioma do jogo.
 */
enum class ReportCategory(val kind: ReportKind, val label: String, @StringRes val nameRes: Int) {
    CRASH(ReportKind.PROBLEM, "App travou ou fechou sozinho", R.string.report_category_crash),
    SLOW(ReportKind.PROBLEM, "Jogo lento ou engasgando", R.string.report_category_slow),
    SYNC(ReportKind.PROBLEM, "Som ou notas fora de sincronia", R.string.report_category_sync),
    CHART(ReportKind.PROBLEM, "Mapa ou notas erradas", R.string.report_category_chart),
    LIBRARY(ReportKind.PROBLEM, "Biblioteca e importar mapas", R.string.report_category_library),
    ENCORE(ReportKind.PROBLEM, "Pesquisa e download do Encore", R.string.report_category_encore),
    AI(ReportKind.PROBLEM, "Guitarra separada por IA", R.string.report_category_ai),
    OTHER_PROBLEM(ReportKind.PROBLEM, "Outro problema", R.string.report_category_other_problem),
    SONGS(ReportKind.SUGGESTION, "Músicas e mapas", R.string.report_category_songs),
    GAMEPLAY(ReportKind.SUGGESTION, "Jogabilidade", R.string.report_category_gameplay),
    LOOK(ReportKind.SUGGESTION, "Visual da pista", R.string.report_category_look),
    SOUNDS(ReportKind.SUGGESTION, "Sons", R.string.report_category_sounds),
    SCREENS(ReportKind.SUGGESTION, "Telas e menus", R.string.report_category_screens),
    OTHER_SUGGESTION(ReportKind.SUGGESTION, "Outra sugestão", R.string.report_category_other_suggestion),
    ;

    companion object {
        /** As categorias de um tipo; "Outro" não tem. */
        fun forKind(kind: ReportKind): List<ReportCategory> = entries.filter { it.kind == kind }
    }
}

/** Informações do aparelho que ajudam a reproduzir o problema (a pessoa vê o que vai e pode desligar). */
data class DeviceInfo(val appVersion: String, val android: String, val model: String, val language: String)

data class BugReport(
    val kind: ReportKind,
    val text: String,
    val contact: String?,
    val device: DeviceInfo?,
    val category: ReportCategory? = null,
    /** Categoria escrita pela pessoa, em "Outro". */
    val customCategory: String? = null,
) {
    fun toJson(): String = JSONObject().apply {
        put("tipo", kind.code)
        (category?.label ?: customCategory?.takeIf { it.isNotBlank() })?.let { put("categoria", it) }
        put("texto", text)
        contact?.takeIf { it.isNotBlank() }?.let { put("contato", it) }
        device?.let {
            put(
                "aparelho",
                JSONObject()
                    .put("versaoDoApp", it.appVersion)
                    .put("android", it.android)
                    .put("modelo", it.model)
                    .put("idioma", it.language),
            )
        }
    }.toString()
}
