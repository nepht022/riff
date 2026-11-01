// O FFmpeg do PC traz bibliotecas nativas por sistema: só a do sistema em que se compila (Windows no PC do
// usuário, Linux no CI).
val nativeClassifier: String = System.getProperty("os.name").lowercase().let { os ->
    val arm = System.getProperty("os.arch").lowercase().let { it == "aarch64" || it == "arm64" }
    when {
        os.startsWith("windows") -> "windows-x86_64"
        os.startsWith("mac") -> if (arm) "macosx-arm64" else "macosx-x86_64"
        else -> if (arm) "linux-arm64" else "linux-x86_64"
    }
}

plugins {
    alias(libs.plugins.riff.kmp.library)
    alias(libs.plugins.riff.kmp.hilt)
}

kotlin {
    (this as ExtensionAware).extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget>(
        "android",
    ) {
        // Os sons do app (res/raw) para o SoundPool.
        androidResources.enable = true
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:chart"))
            implementation(libs.kotlinx.coroutines.core)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.bytedeco.ffmpeg)
            implementation(libs.bytedeco.javacpp)
            listOf(libs.bytedeco.ffmpeg, libs.bytedeco.javacpp).forEach { library ->
                implementation(library.map { "${it.module}:${it.versionConstraint.requiredVersion}:$nativeClassifier" })
            }
        }
        getByName("desktopTest").dependencies {
            implementation(project.dependencies.testFixtures(project(":core:chart")))
        }
        getByName("androidHostTest").dependencies {
            implementation(project.dependencies.testFixtures(project(":core:chart")))
            implementation(libs.androidx.test.core)
        }
    }
}
