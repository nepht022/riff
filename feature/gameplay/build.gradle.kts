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
            // A skia nativa do sistema (decodificar a imagem do mapa nos testes do PC), a janela de teste do Compose
            // e o Roborazzi do desktop (a partida deitada).
            implementation(compose.desktop.currentOs)
            implementation(compose.desktop.uiTestJUnit4)
            implementation(libs.roborazzi.compose.desktop)
        }
        androidMain.dependencies {
            // BackHandler (Voltar do sistema pausa a partida).
            implementation(libs.androidx.activity.compose)
        }
    }
}
