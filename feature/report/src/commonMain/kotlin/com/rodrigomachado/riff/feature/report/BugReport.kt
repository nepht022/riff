package com.rodrigomachado.riff.feature.report

import com.rodrigomachado.riff.feature.report.resources.Res
import com.rodrigomachado.riff.feature.report.resources.report_category_ai
import com.rodrigomachado.riff.feature.report.resources.report_category_chart
import com.rodrigomachado.riff.feature.report.resources.report_category_crash
import com.rodrigomachado.riff.feature.report.resources.report_category_encore
import com.rodrigomachado.riff.feature.report.resources.report_category_gameplay
import com.rodrigomachado.riff.feature.report.resources.report_category_library
import com.rodrigomachado.riff.feature.report.resources.report_category_look
import com.rodrigomachado.riff.feature.report.resources.report_category_other_problem
import com.rodrigomachado.riff.feature.report.resources.report_category_other_suggestion
import com.rodrigomachado.riff.feature.report.resources.report_category_screens
import com.rodrigomachado.riff.feature.report.resources.report_category_slow
import com.rodrigomachado.riff.feature.report.resources.report_category_songs
import com.rodrigomachado.riff.feature.report.resources.report_category_sounds
import com.rodrigomachado.riff.feature.report.resources.report_category_sync
import org.jetbrains.compose.resources.StringResource

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
enum class ReportCategory(val kind: ReportKind, val label: String, val nameRes: StringResource) {
    CRASH(ReportKind.PROBLEM, "App travou ou fechou sozinho", Res.string.report_category_crash),
    SLOW(ReportKind.PROBLEM, "Jogo lento ou engasgando", Res.string.report_category_slow),
    SYNC(ReportKind.PROBLEM, "Som ou notas fora de sincronia", Res.string.report_category_sync),
    CHART(ReportKind.PROBLEM, "Mapa ou notas erradas", Res.string.report_category_chart),
    LIBRARY(ReportKind.PROBLEM, "Biblioteca e importar mapas", Res.string.report_category_library),
    ENCORE(ReportKind.PROBLEM, "Pesquisa e download do Encore", Res.string.report_category_encore),
    AI(ReportKind.PROBLEM, "Guitarra separada por IA", Res.string.report_category_ai),
    OTHER_PROBLEM(ReportKind.PROBLEM, "Outro problema", Res.string.report_category_other_problem),
    SONGS(ReportKind.SUGGESTION, "Músicas e mapas", Res.string.report_category_songs),
    GAMEPLAY(ReportKind.SUGGESTION, "Jogabilidade", Res.string.report_category_gameplay),
    LOOK(ReportKind.SUGGESTION, "Visual da pista", Res.string.report_category_look),
    SOUNDS(ReportKind.SUGGESTION, "Sons", Res.string.report_category_sounds),
    SCREENS(ReportKind.SUGGESTION, "Telas e menus", Res.string.report_category_screens),
    OTHER_SUGGESTION(ReportKind.SUGGESTION, "Outra sugestão", Res.string.report_category_other_suggestion),
    ;

    companion object {
        /** As categorias de um tipo; "Outro" não tem. */
        fun forKind(kind: ReportKind): List<ReportCategory> = entries.filter { it.kind == kind }
    }
}

/**
 * Informações do aparelho que ajudam a reproduzir o problema (a pessoa vê o que vai e pode desligar). [system] é o
 * texto da tela ("Android 16", "Windows 11 10.0 (amd64)"); [model], o aparelho (no PC, null: o nome do computador é
 * dado pessoal e não ajuda); [systemField], como o sistema vai no JSON.
 */
data class DeviceInfo(
    val appVersion: String,
    val system: String,
    val model: String?,
    val language: String,
    val systemField: SystemField = SystemField(SYSTEM_KEY, system),
) {
    /** A linha da tela: exatamente o que vai junto. */
    val summary: String get() = listOfNotNull("Riff $appVersion", system, model, language).joinToString(" · ")

    companion object {
        /** A chave do sistema no JSON do PC (texto inteiro, como na tela). */
        const val SYSTEM_KEY = "sistema"

        /** No celular o JSON leva `android` só com a versão, como sempre foi (o script do Reportar já lê assim). */
        fun android(appVersion: String, release: String, model: String, language: String) = DeviceInfo(
            appVersion = appVersion,
            system = "Android $release",
            model = model,
            language = language,
            systemField = SystemField("android", release),
        )
    }
}

/** Uma chave do JSON e o seu valor. */
data class SystemField(val key: String, val value: String)

data class BugReport(
    val kind: ReportKind,
    val text: String,
    val contact: String?,
    val device: DeviceInfo?,
    val category: ReportCategory? = null,
    /** Categoria escrita pela pessoa, em "Outro". */
    val customCategory: String? = null,
) {
    /** O JSON do relato, na ordem e com os escapes do org.json do Android (o mesmo texto de antes, byte a byte). */
    fun toJson(): String = JsonWriter().obj {
        field("tipo", kind.code)
        (category?.label ?: customCategory?.takeIf { it.isNotBlank() })?.let { field("categoria", it) }
        field("texto", text)
        contact?.takeIf { it.isNotBlank() }?.let { field("contato", it) }
        device?.let { device ->
            objField("aparelho") {
                field("versaoDoApp", device.appVersion)
                field(device.systemField.key, device.systemField.value)
                device.model?.let { field("modelo", it) }
                field("idioma", device.language)
            }
        }
    }
}
