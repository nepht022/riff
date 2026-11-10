plugins {
    alias(libs.plugins.riff.kmp.feature)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.kover)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:room"))
        }
        androidMain.dependencies {
            // BackHandler (o Voltar do sistema na sala também sai dela).
            implementation(libs.androidx.activity.compose)
        }
        getByName("androidHostTest").dependencies {
            // O Firebase falso: nenhum teste fala com o Firebase de verdade.
            implementation(project(":core:room:testing"))
        }
    }
}
