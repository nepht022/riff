import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// O Riff no PC (Windows): a mesma base de telas do celular, com Compose Multiplatform para desktop.

// O GLFW (controle) traz bibliotecas nativas por sistema: só a do sistema em que se compila (Windows no PC do
// usuário, Linux no CI).
val lwjglNatives: String = System.getProperty("os.name").lowercase().let { os ->
    val arm = System.getProperty("os.arch").lowercase().let { it == "aarch64" || it == "arm64" }
    when {
        os.startsWith("windows") -> "natives-windows"
        os.startsWith("mac") -> if (arm) "natives-macos-arm64" else "natives-macos"
        else -> if (arm) "natives-linux-arm64" else "natives-linux"
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.roborazzi)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":feature:home"))
    implementation(project(":feature:library"))
    implementation(project(":feature:gameplay"))
    implementation(project(":feature:results"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:calibration"))
    implementation(project(":feature:search"))
    implementation(project(":core:encore"))
    implementation(project(":core:game"))
    implementation(project(":core:data"))
    implementation(project(":core:audio"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(libs.jetbrains.navigation.compose)
    implementation(libs.jetbrains.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(project(":core:chart"))
    implementation(libs.androidx.room.runtime)
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.mp.material3)
    listOf(libs.lwjgl.core, libs.lwjgl.glfw).forEach { library ->
        implementation(library)
        runtimeOnly(library.map { "${it.module}:${it.versionConstraint.requiredVersion}:$lwjglNatives" })
    }
    testImplementation(libs.junit4)
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.roborazzi.compose.desktop)
    // Os pacotes .sng do Encore falso da pesquisa.
    testImplementation(testFixtures(project(":core:chart")))
}

compose.desktop {
    application {
        mainClass = "com.rodrigomachado.riff.desktop.MainKt"
    }
}

// Verificador do controle no PC: ./scripts/gw :desktop:runPadCheck (20 s; aperte os botões)
tasks.register<JavaExec>("runPadCheck") {
    group = "riff"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.rodrigomachado.riff.desktop.pad.PadCheckKt")
}

// Medição do áudio no PC: ./scripts/gw :desktop:runAudioCheck --args="<pasta do mapa ou .sng>"
tasks.register<JavaExec>("runAudioCheck") {
    group = "riff"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.rodrigomachado.riff.desktop.AudioCheckKt")
}
