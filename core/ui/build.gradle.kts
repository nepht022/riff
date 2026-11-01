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
        androidMain.dependencies {
            // No Android, o Compose do BOM, como nos outros módulos do celular.
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.material3)
            implementation(libs.androidx.compose.ui.tooling.preview)
        }
    }
}
