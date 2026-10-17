import java.util.Properties

plugins {
    alias(libs.plugins.riff.android.library)
    alias(libs.plugins.riff.android.hilt)
    alias(libs.plugins.kover)
}

// Endereço do script do ranking (Google Apps Script) no local.properties, fora do git. Sem ele o ranking fica
// desligado.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.rodrigomachado.riff.core.data"
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "RANKING_URL", "\"${localProperties.getProperty("ranking.url", "")}\"")
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:chart"))
    implementation(project(":core:encore"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:spotify"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
    testImplementation(testFixtures(project(":core:chart")))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    // O teste do repositório cria o banco em memória; o Room chega a :core:database só como implementation.
    testImplementation(libs.androidx.room.runtime)
}
