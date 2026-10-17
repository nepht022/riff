plugins {
    alias(libs.plugins.riff.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.rodrigomachado.riff.feature.settings"
}

dependencies {
    // Ouvir o som de acerto e de erro enquanto ajusta o volume.
    implementation(project(":core:audio"))
    // Autorização da API Web do Spotify (ler a fila) ao ligar "Baixar mapas da fila".
    implementation(project(":core:spotify"))
}
