import java.util.Properties

plugins {
    alias(libs.plugins.riff.kmp.feature)
    alias(libs.plugins.roborazzi)
}

// Endereço do script que recebe os relatos (Google Apps Script) no local.properties, fora do git; a versão do Riff
// no gradle.properties (a mesma do app). Viram as constantes de ReportConfig no código comum (o plugin
// multiplataforma não tem BuildConfig). Sem o endereço o build segue e a tela avisa que o envio não está configurado.
val reportUrl: String = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}.getProperty("report.url", "")
val appVersion: String = providers.gradleProperty("riff.versionName").get()
val reportConfigDir = layout.buildDirectory.dir("generated/reportConfig/commonMain/kotlin")
val generateReportConfig = tasks.register("generateReportConfig") {
    inputs.property("reportUrl", reportUrl)
    inputs.property("appVersion", appVersion)
    outputs.dir(reportConfigDir)
    doLast {
        val file = reportConfigDir.get().file("com/rodrigomachado/riff/feature/report/ReportConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package com.rodrigomachado.riff.feature.report\n\n" +
                "/** Gerado no build: o endereço do Reportar (local.properties) e a versão do Riff (gradle.properties). */\n" +
                "object ReportConfig {\n" +
                "    const val REPORT_URL = \"$reportUrl\"\n" +
                "    const val APP_VERSION = \"$appVersion\"\n" +
                "}\n",
        )
    }
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generateReportConfig)
        }
    }
}
