plugins {
    alias(libs.plugins.riff.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.rodrigomachado.riff.feature.gameplay"
}

dependencies {
    implementation(project(":core:game"))
    implementation(project(":core:audio"))
}
