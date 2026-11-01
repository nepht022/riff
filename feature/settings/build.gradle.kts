plugins {
    alias(libs.plugins.riff.kmp.feature)
    alias(libs.plugins.roborazzi)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Ouvir o som de acerto e de erro enquanto ajusta o volume.
            implementation(project(":core:audio"))
        }
        androidMain.dependencies {
            // LocalActivity (refazer a tela ao trocar o idioma antes do Android 13).
            implementation(libs.androidx.activity.compose)
        }
    }
}
