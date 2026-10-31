plugins {
    alias(libs.plugins.riff.kmp.library.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:ui"))
            implementation(project(":core:model"))
            implementation(libs.kotlinx.serialization.core)
        }
    }
}
