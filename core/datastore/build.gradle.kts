plugins {
    alias(libs.plugins.riff.kmp.library)
    alias(libs.plugins.riff.kmp.hilt)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.androidx.datastore.preferences)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
