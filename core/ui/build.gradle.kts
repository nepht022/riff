plugins {
    alias(libs.plugins.riff.kmp.library.compose)
    alias(libs.plugins.roborazzi)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(libs.jetbrains.lifecycle.runtime.compose)
        }
        getByName("desktopTest").dependencies {
            // Testes de tela no PC: a janela de teste do Compose e o Roborazzi do desktop.
            implementation(compose.desktop.currentOs)
            implementation(compose.desktop.uiTestJUnit4)
            implementation(libs.roborazzi.compose.desktop)
        }
        androidMain.dependencies {
            // No Android, o Compose do BOM, como nos outros módulos do celular.
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.material3)
            implementation(libs.androidx.compose.ui.tooling.preview)
        }
    }
}
