package com.rodrigomachado.riff.feature.report

import org.json.JSONObject

/** O que a pessoa está relatando; [code] é como aparece no email. */
enum class ReportKind(val code: String) {
    PROBLEM("problema"),
    SUGGESTION("sugestao"),
    OTHER("outro"),
}

/** Informações do aparelho que ajudam a reproduzir o problema (a pessoa vê o que vai e pode desligar). */
data class DeviceInfo(val appVersion: String, val android: String, val model: String, val language: String)

data class BugReport(val kind: ReportKind, val text: String, val contact: String?, val device: DeviceInfo?) {
    fun toJson(): String = JSONObject().apply {
        put("tipo", kind.code)
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
