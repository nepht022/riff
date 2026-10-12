plugins {
    alias(libs.plugins.riff.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.rodrigomachado.riff.feature.search"
}

dependencies {
    implementation(project(":core:encore"))
    implementation(project(":core:chart"))
    // Seletor de pasta do sistema (OpenDocumentTree), para pedir a permissão de gravar.
    implementation(libs.androidx.activity.compose)
    testImplementation(testFixtures(project(":core:chart")))
}
