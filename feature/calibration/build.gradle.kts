plugins {
    alias(libs.plugins.riff.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.rodrigomachado.riff.feature.calibration"
}

dependencies {
    // A saída de áudio que informa quando cada som sai no alto-falante.
    implementation(project(":core:audio"))
}
