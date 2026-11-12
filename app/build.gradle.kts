import java.util.Properties

plugins {
    alias(libs.plugins.riff.android.application.compose)
    alias(libs.plugins.riff.android.hilt)
    alias(libs.plugins.baselineprofile)
    // Gera a lista das bibliotecas (com as licenças) para a tela Licenças e privacidade.
    alias(libs.plugins.aboutlibraries)
}

// A chave de release fica fora do git (a senha no local.properties). Sem ela, o build usa a chave de debug.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
// Fica em ~/.android/riff-release.jks (o mesmo lugar da chave de debug), fora do projeto.
val releaseKeystore = file("${System.getProperty("user.home")}/.android/riff-release.jks")
    .takeIf { it.exists() && localProperties.getProperty("riff.keystore.password") != null }

// Um release com a chave de debug não instala por cima do APK de quem já tem o Riff: sem a chave própria, para aqui.
gradle.taskGraph.whenReady {
    if (releaseKeystore == null && allTasks.any { it.path == ":app:packageRelease" }) {
        throw GradleException(
            "Release sem a chave própria: crie ~/.android/riff-release.jks e ponha riff.keystore.password no " +
                "local.properties (README, Chave de release).",
        )
    }
}

android {
    namespace = "com.rodrigomachado.riff"
    defaultConfig {
        applicationId = "com.rodrigomachado.riff"
        versionCode = 8
        // A versão mora no gradle.properties: o Reportar do PC lê a mesma.
        versionName = providers.gradleProperty("riff.versionName").get()
        // Só celulares (ARM): as bibliotecas x86 do ONNX Runtime (~80 MB) servem apenas a emuladores e PCs.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    // O modelo da separação por IA é mapeado direto do APK: o asset não pode ser comprimido (só aqui vale).
    androidResources {
        noCompress += "onnx"
    }
    signingConfigs {
        if (releaseKeystore != null) {
            create("riff") {
                storeFile = releaseKeystore
                storePassword = localProperties.getProperty("riff.keystore.password")
                keyAlias = localProperties.getProperty("riff.key.alias", "riff")
                keyPassword = localProperties.getProperty("riff.keystore.password")
            }
        }
    }
    buildTypes {
        // Debug e release com a mesma chave: um substitui o outro sem apagar os dados (o desempenho.sh troca os dois).
        val signing = signingConfigs.findByName("riff") ?: signingConfigs.getByName("debug")
        debug {
            signingConfig = signing
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signing
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // Se o Riff está à vista (a fila do Spotify só roda com ele aberto).
    implementation(libs.androidx.lifecycle.process)
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:audio"))
    implementation(project(":core:data"))
    implementation(project(":core:separation"))
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(project(":feature:library"))
    // A rota da Biblioteca cria o ViewModel com hiltViewModel() (a tela comum recebe pronto).
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(project(":feature:search"))
    implementation(project(":feature:gameplay"))
    implementation(project(":feature:results"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:calibration"))
    implementation(project(":feature:report"))
    implementation(project(":feature:ranking"))
    implementation(project(":feature:licenses"))
    implementation(project(":feature:home"))
    implementation(project(":feature:room"))
    // A Sala só liga com o Firebase configurado (RoomSwitch); o módulo do Hilt da Sala vem dele.
    implementation(project(":core:room"))
    // O relógio da partida (TimeSource) para o começo da partida de sala (RoomMatchModule).
    implementation(project(":core:game"))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

// O Robolectric codifica espaços do caminho do ~/.m2 como %20 (veja o AndroidLibraryConventionPlugin).
providers.gradleProperty("riff.robolectric.mavenRepoLocal").orNull?.let { repo ->
    tasks.withType<Test>().configureEach { systemProperty("maven.repo.local", repo) }
}
