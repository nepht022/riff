plugins {
    alias(libs.plugins.riff.android.library)
    alias(libs.plugins.riff.android.hilt)
}

android {
    namespace = "com.rodrigomachado.riff.core.encore"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:chart"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(testFixtures(project(":core:chart")))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
