plugins {
    alias(libs.plugins.riff.android.library)
    alias(libs.plugins.riff.android.hilt)
}

android {
    namespace = "com.rodrigomachado.riff.core.audio"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:chart"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
    testImplementation(testFixtures(project(":core:chart")))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
