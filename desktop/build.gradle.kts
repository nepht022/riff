import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream
import java.time.Duration

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
    // A conferência das peças nativas (InstallCheck) chama o ONNX Runtime e o SQLite embutido direto; os dois já vêm
    // no runtime pelo :core:separation e pelo :core:database, na mesma versão.
    implementation(libs.onnxruntime.jvm)
    implementation(libs.androidx.sqlite.bundled)
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
    // O ícone da janela: o mesmo desenho do instalador (desktop/icons), sem uma segunda cópia no git.
    from("icons/riff.svg") { into("riff") }
}

// O Java que vai no instalador (e que roda o :desktop:run e o :desktop:runInstallCheck): o Temurin 21 baixado pela
// toolchain do Gradle. O JBR do build não tem o jpackage nem o jdk.crypto.mscapi; o Oracle JDK não é redistribuível.
val temurin21 = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(21)
    vendor = JvmVendorSpec.ADOPTIUM
}

compose.desktop {
    application {
        mainClass = "com.rodrigomachado.riff.desktop.MainKt"
        javaHome = temurin21.get().metadata.installationPath.asFile.absolutePath
        // Proxy e certificados do Windows (antivírus ou rede que inspecionam o HTTPS).
        jvmArgs += listOf("-Djava.net.useSystemProxies=true", "-Djavax.net.ssl.trustStoreType=Windows-ROOT")
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Riff"
            packageVersion = providers.gradleProperty("riff.versionName").get()
            vendor = "Rodrigo Machado"
            description = "Riff — jogo de ritmo com guitarra"
            // Os módulos do Java empacotado: os do ./scripts/gw :desktop:suggestRuntimeModules mais o jdk.crypto.ec
            // (curvas do HTTPS), o jdk.crypto.mscapi (o KeyStore Windows-ROOT), o jdk.localedata (números e datas em
            // português) e o jdk.unsupported (garantidos). O suggest (jdeps) não vê módulos carregados por serviço,
            // como esses três primeiros: sem eles o app abre, mas sem a parte. O :desktop:verifyDistributable prova
            // que bastam.
            modules(
                "java.instrument",
                "jdk.management",
                "jdk.unsupported",
                "jdk.crypto.ec",
                "jdk.crypto.mscapi",
                "jdk.localedata",
            )
            windows {
                // Instala no perfil do usuário, sem pedir administrador; atalho no menu Iniciar e na área de trabalho.
                perUserInstall = true
                menu = true
                menuGroup = "Riff"
                shortcut = true
                dirChooser = false
                // Fixo: um .msi novo substitui o antigo.
                upgradeUuid = "5b0f1a7e-3c2d-4e8a-9f61-7d2b4c8e1a90"
                iconFile.set(project.file("icons/riff.ico"))
            }
        }
    }
}

// A conferência das peças nativas dentro do app empacotado, no Java que vai junto: ./scripts/gw
// :desktop:verifyDistributable (uma linha `ok` por peça e o `total`; falha com qualquer `FALHOU`). O jlink do Compose
// tira o java.exe do runtime; por isso a conferência roda por um segundo lançador do jpackage (RiffCheck.exe, cópia
// do Riff.exe, com o RiffCheck.cfg trocando a classe principal), numa cópia do app: o mesmo runtime e as mesmas
// opções da JVM do Riff.
val distributable = layout.buildDirectory.dir("compose/binaries/main/app/Riff")
val distributableCheck = layout.buildDirectory.dir("compose/check/Riff")
val prepareDistributableCheck = tasks.register<Sync>("prepareDistributableCheck") {
    dependsOn("createDistributable")
    from(distributable)
    from(distributable.map { it.file("Riff.exe") }) { rename { "RiffCheck.exe" } }
    from(distributable.map { it.file("app/Riff.cfg") }) {
        into("app")
        rename { "RiffCheck.cfg" }
        filter { it.replace("desktop.MainKt", "desktop.InstallCheckKt") }
    }
    into(distributableCheck)
}
tasks.register<Exec>("verifyDistributable") {
    group = "riff"
    dependsOn(prepareDistributableCheck)
    workingDir(distributableCheck)
    // O caminho do repositório tem espaços: o executável vai inteiro, sem passar por uma linha de comando.
    executable(distributableCheck.get().file("RiffCheck.exe").asFile.absolutePath)
    // O lançador sem console pode abrir uma caixa de erro (classe faltando) e esperar alguém fechar.
    timeout.set(Duration.ofMinutes(5))
    val output = ByteArrayOutputStream()
    standardOutput = output
    isIgnoreExitValue = true
    doLast {
        val text = output.toString()
        print(text)
        val code = executionResult.get().exitValue
        val lines = text.lines().map { it.trim() }
        val oks = lines.count { it.startsWith("ok ") }
        // O total vem do próprio InstallCheck: uma peça nova não exige mudar a conta aqui.
        val total = lines.lastOrNull { it.startsWith("total ") }?.removePrefix("total ")?.toIntOrNull()
        check(code == 0 && total != null && oks == total && !text.contains("FALHOU")) {
            "a conferência do app empacotado falhou (código $code, $oks ok de ${total ?: "?"})"
        }
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

// Conferência das peças nativas no Temurin 21 do instalador (fora do pacote): ./scripts/gw :desktop:runInstallCheck
// (uma linha `ok` por peça e o `total`)
tasks.register<JavaExec>("runInstallCheck") {
    group = "riff"
    javaLauncher.set(temurin21)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.rodrigomachado.riff.desktop.InstallCheckKt")
}

// O DistributableTest lê o app empacotado (pulado quando ele não existe): a pasta é entrada do teste, para ele rodar
// de novo quando o pacote muda.
tasks.test {
    inputs.files(fileTree(layout.buildDirectory.dir("compose/binaries/main/app")))
        .withPropertyName("distributable").optional()
    mustRunAfter("createDistributable")
}
