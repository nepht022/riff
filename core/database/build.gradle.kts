plugins {
    alias(libs.plugins.riff.android.library)
    alias(libs.plugins.riff.android.room)
    alias(libs.plugins.riff.android.hilt)
}

android {
    namespace = "com.rodrigomachado.riff.core.database"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
}

// Os esquemas exportados viram assets dos testes, para o teste de migração (DSL nova do AGP 9).
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
}
