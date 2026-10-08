plugins {
    alias(libs.plugins.riff.android.library)
    alias(libs.plugins.riff.android.hilt)
}

android {
    namespace = "com.rodrigomachado.riff.core.datastore"
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
