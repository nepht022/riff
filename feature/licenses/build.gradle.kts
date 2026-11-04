plugins {
    alias(libs.plugins.riff.kmp.feature)
    alias(libs.plugins.roborazzi)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // A lista das bibliotecas (no celular, gerada no build do app pelo plugin AboutLibraries).
            implementation(libs.aboutlibraries.core)
        }
    }
}
