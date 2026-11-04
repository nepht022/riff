plugins {
    alias(libs.plugins.riff.kmp.feature)
    alias(libs.plugins.roborazzi)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:encore"))
            implementation(project(":core:chart"))
        }
        commonTest.dependencies {
            // Os pacotes .sng falsos do transporte de teste.
            implementation(project.dependencies.testFixtures(project(":core:chart")))
            // O org.json vem com o Android; no PC entra a biblioteca original.
            compileOnly(libs.org.json)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.org.json)
            // A janela de teste do Compose (a pesquisa só com o teclado).
            implementation(compose.desktop.currentOs)
            implementation(compose.desktop.uiTestJUnit4)
        }
        androidMain.dependencies {
            // Seletor de pasta do sistema (OpenDocumentTree), para pedir a permissão de gravar.
            implementation(libs.androidx.activity.compose)
        }
    }
}
