plugins {
    alias(libs.plugins.riff.kmp.feature)
    alias(libs.plugins.roborazzi)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // A saída de áudio que informa quando cada som sai no alto-falante.
            implementation(project(":core:audio"))
        }
        getByName("desktopTest").dependencies {
            // A janela de teste do Compose (as teclas e o foco da janela no PC), o Main do PC (o viewModelScope) e o
            // Roborazzi do desktop (a calibração na janela larga).
            implementation(compose.desktop.currentOs)
            implementation(compose.desktop.uiTestJUnit4)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.roborazzi.compose.desktop)
        }
    }
}
