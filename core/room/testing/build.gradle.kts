plugins {
    alias(libs.plugins.riff.kmp.library)
}

// O Firebase falso da Sala (em memória, sem regras) para os testes do :core:room, do :feature:room e do :desktop.
// Só entra nas dependências de teste: nunca vai no app.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:room"))
            implementation(libs.kotlinx.coroutines.core)
            // O org.json vem com o Android (nos testes, o do Robolectric); no PC entra a biblioteca original.
            compileOnly(libs.org.json)
        }
        getByName("desktopMain").dependencies { implementation(libs.org.json) }
    }
}
