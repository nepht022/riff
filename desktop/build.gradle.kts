import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// O Riff no PC (Windows): a mesma base de telas do celular, com Compose Multiplatform para desktop.
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
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.mp.material3)
    testImplementation(libs.junit4)
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.roborazzi.compose.desktop)
}

compose.desktop {
    application {
        mainClass = "com.rodrigomachado.riff.desktop.MainKt"
    }
}
