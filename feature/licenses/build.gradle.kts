plugins {
    alias(libs.plugins.riff.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.rodrigomachado.riff.feature.licenses"
}

dependencies {
    // A lista das bibliotecas, gerada no build do app pelo plugin AboutLibraries.
    implementation(libs.aboutlibraries.core)
}
