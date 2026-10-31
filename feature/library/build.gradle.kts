plugins {
    alias(libs.plugins.riff.kmp.feature)
    alias(libs.plugins.roborazzi)
}

kotlin {
    sourceSets {
        androidMain.dependencies {
            // Seletor de pasta do sistema (OpenDocumentTree) e pedido de permissão de notificação.
            implementation(libs.androidx.activity.compose)
        }
    }
}
