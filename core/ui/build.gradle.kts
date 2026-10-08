plugins {
    alias(libs.plugins.riff.android.library.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.rodrigomachado.riff.core.ui"
}

dependencies {
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
