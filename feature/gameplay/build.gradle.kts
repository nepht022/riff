plugins {
    alias(libs.plugins.riff.kmp.feature)
    alias(libs.plugins.roborazzi)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:game"))
            implementation(project(":core:audio"))
        }
        getByName("desktopTest").dependencies {
            // A skia nativa do sistema (decodificar a imagem do mapa nos testes do PC).
            implementation(compose.desktop.currentOs)
        }
        androidMain.dependencies {
            // BackHandler (Voltar do sistema pausa a partida).
            implementation(libs.androidx.activity.compose)
        }
    }
}
