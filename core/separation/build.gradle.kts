plugins {
    alias(libs.plugins.riff.kmp.library)
    alias(libs.plugins.riff.kmp.hilt)
}

kotlin {
    // A base dos testes comuns que logam (Robolectric no Android, JUnit puro no PC).
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    (this as ExtensionAware).extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget>(
        "android",
    ) {
        // As notificações (strings e ícone) e o modelo (asset).
        androidResources.enable = true
        // O ONNX Runtime chama as classes Java pelo JNI: o R8 do app não pode renomeá-las nem tirá-las.
        optimization { consumerKeepRules.apply { publish = true; file("consumer-rules.pro") } }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:audio"))
            implementation(project(":core:chart"))
            implementation(project(":core:data"))
            implementation(libs.jtransforms)
            implementation(libs.kotlinx.coroutines.core)
            // A API ai.onnxruntime é a mesma nos dois artefatos: o comum compila contra o da JVM, cada alvo leva o seu.
            compileOnly(libs.onnxruntime.jvm)
        }
        androidMain.dependencies {
            implementation(libs.onnxruntime.android)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.work.runtime)
            implementation(libs.androidx.hilt.work)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.onnxruntime.jvm)
            // O AAC das faixas pelo FFmpeg do :core:audio (as bibliotecas nativas vêm dele).
            implementation(libs.bytedeco.ffmpeg)
            implementation(libs.bytedeco.javacpp)
        }
        getByName("desktopTest").dependencies {
            implementation(project.dependencies.testFixtures(project(":core:chart")))
        }
        commonTest.dependencies {
            implementation(libs.junit4)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            // O onnxruntime-android não traz bibliotecas nativas para o PC: os testes do Android usam o de desktop.
            implementation(libs.onnxruntime.jvm)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.work.testing)
        }
    }
}

configurations.matching { it.name.startsWith("androidHostTest") && it.name.endsWith("RuntimeClasspath") }.configureEach {
    exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime-android")
}
// O código do Android compila só contra o onnxruntime-android (o da JVM 1.18 não abre de um ByteBuffer).
configurations.matching { it.name == "androidCompileClasspath" }.configureEach {
    exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime")
}

// O modelo (130 MB, Git LFS) continua em src/main/assets, no mesmo caminho do APK de antes e lido pelos testes e
// pelo PC: um arquivo só no repositório.
androidComponents {
    onVariants { variant -> variant.sources.assets?.addStaticSourceDirectory("$projectDir/src/main/assets") }
}

dependencies {
    add("kspAndroid", libs.androidx.hilt.compiler)
}
