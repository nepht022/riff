import java.util.Properties

plugins {
    alias(libs.plugins.riff.android.library)
    alias(libs.plugins.riff.android.hilt)
}

// Credenciais do Spotify ficam no local.properties (fora do git). Sem elas o build segue e a tela avisa.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.rodrigomachado.riff.core.spotify"
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${localProperties.getProperty("spotify.clientId", "")}\"")
        buildConfigField(
            "String",
            "SPOTIFY_REDIRECT_URI",
            "\"${localProperties.getProperty("spotify.redirectUri", "riff://callback")}\"",
        )
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:audio"))
    implementation(libs.spotify.app.remote)
    implementation(libs.spotify.auth)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core.ktx)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
