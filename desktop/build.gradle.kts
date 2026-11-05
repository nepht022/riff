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
    alias(libs.plugins.aboutlibraries.jvm)
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
    implementation(project(":feature:report"))
    implementation(project(":feature:licenses"))
    implementation(project(":feature:ranking"))
    implementation(project(":core:encore"))
    implementation(project(":core:game"))
    implementation(project(":core:data"))
    implementation(project(":core:audio"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:separation"))
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
    // O JSON dos pedidos ao script falso do ranking (o mesmo org.json do :core:data no PC).
    testImplementation(libs.org.json)
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.roborazzi.compose.desktop)
    // Os pacotes .sng do Encore falso da pesquisa.
    testImplementation(testFixtures(project(":core:chart")))
}

// A lista das bibliotecas que vão no app do PC (o que o Gradle põe no runtimeClasspath), gerada no build pelo
// AboutLibraries e lida pela tela de Licenças nos recursos (aboutlibraries.json). O celular tem a sua, no :app.
val librariesDir = layout.buildDirectory.dir("generated/aboutLibraries/desktop")
aboutLibraries {
    export {
        outputFile = librariesDir.map { it.file("aboutlibraries.json") }
    }
}
sourceSets["main"].resources.srcDir(files(librariesDir).builtBy("exportLibraryDefinitions"))

// O modelo da IA (130 MB, Git LFS) é o mesmo asset do APK: copiado para os recursos (`/riff/...`) no build, sem
// uma segunda cópia no git.
tasks.processResources {
    from(rootDir.resolve("core/separation/src/main/assets/htdemucs_6s_seg4.onnx")) { into("riff") }
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

// Medição da separação por IA no PC: ./scripts/gw :desktop:runSeparationCheck --args="<pasta do mapa ou .sng>"
tasks.register<JavaExec>("runSeparationCheck") {
    group = "riff"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.rodrigomachado.riff.desktop.SeparationCheckKt")
}

// Medição do áudio no PC: ./scripts/gw :desktop:runAudioCheck --args="<pasta do mapa ou .sng>"
tasks.register<JavaExec>("runAudioCheck") {
    group = "riff"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.rodrigomachado.riff.desktop.AudioCheckKt")
}
