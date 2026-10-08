plugins {
    alias(libs.plugins.riff.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.rodrigomachado.riff.feature.library"
}

dependencies {
    // Seletor de pasta do sistema (OpenDocumentTree) via rememberLauncherForActivityResult.
    implementation(libs.androidx.activity.compose)
}
