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
    implementation(project(":feature:library"))
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
    testImplementation(libs.junit4)
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.roborazzi.compose.desktop)
}

compose.desktop {
    application {
        mainClass = "com.rodrigomachado.riff.desktop.MainKt"
    }
}

// Medição do áudio no PC: ./scripts/gw :desktop:runAudioCheck --args="<pasta do mapa ou .sng>"
tasks.register<JavaExec>("runAudioCheck") {
    group = "riff"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.rodrigomachado.riff.desktop.AudioCheckKt")
}
