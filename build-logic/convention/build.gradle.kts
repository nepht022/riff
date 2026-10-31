import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.rodrigomachado.riff.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        // O Kotlin embutido no Gradle pode ser mais antigo que o KGP 2.4 usado como dependência.
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.composeMultiplatform.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.kotlin.serialization.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "riff.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "riff.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "riff.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "riff.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "riff.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "riff.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("androidFeature") {
            id = "riff.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("kmpLibrary") {
            id = "riff.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpRoom") {
            id = "riff.kmp.room"
            implementationClass = "KmpRoomConventionPlugin"
        }
        register("kmpHilt") {
            id = "riff.kmp.hilt"
            implementationClass = "KmpHiltConventionPlugin"
        }
        register("kmpFeature") {
            id = "riff.kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
        }
        register("kmpLibraryCompose") {
            id = "riff.kmp.library.compose"
            implementationClass = "KmpLibraryComposeConventionPlugin"
        }
        register("jvmLibrary") {
            id = "riff.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
