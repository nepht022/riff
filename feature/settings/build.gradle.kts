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
        getByName("desktopTest").dependencies {
            // A janela de teste do Compose e o Roborazzi do desktop (as Configurações na janela larga).
            implementation(compose.desktop.currentOs)
            implementation(compose.desktop.uiTestJUnit4)
            implementation(libs.roborazzi.compose.desktop)
        }
        androidMain.dependencies {
            // LocalActivity (refazer a tela ao trocar o idioma antes do Android 13).
            implementation(libs.androidx.activity.compose)
        }
    }
}
