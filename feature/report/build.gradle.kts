import java.util.Properties

plugins {
    alias(libs.plugins.riff.android.feature)
    alias(libs.plugins.roborazzi)
}

// Endereço do script que recebe os relatos (Google Apps Script) fica no local.properties, fora do git.
// Sem ele o build segue e a tela avisa que o envio não está configurado.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.rodrigomachado.riff.feature.report"
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "REPORT_URL", "\"${localProperties.getProperty("report.url", "")}\"")
    }
}
