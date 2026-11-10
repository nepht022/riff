import java.util.Properties

plugins {
    alias(libs.plugins.riff.kmp.library)
    alias(libs.plugins.riff.kmp.hilt)
    alias(libs.plugins.kover)
}

// A chave da API e o endereço do banco do Firebase no local.properties, fora do git. Sem eles a Sala fica desligada.
// Viram as constantes RoomConfig.API_KEY e RoomConfig.DB_URL do código comum (como o RankingConfig do :core:data).
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val firebaseApiKey: String = localProperties.getProperty("firebase.apiKey", "")
val firebaseDbUrl: String = localProperties.getProperty("firebase.dbUrl", "")

/** O valor dentro das aspas de uma string do Kotlin. */
fun kotlinLiteral(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

val roomConfigDir = layout.buildDirectory.dir("generated/roomConfig/commonMain/kotlin")
val generateRoomConfig = tasks.register("generateRoomConfig") {
    inputs.property("firebaseApiKey", firebaseApiKey)
    inputs.property("firebaseDbUrl", firebaseDbUrl)
    outputs.dir(roomConfigDir)
    doLast {
        val file = roomConfigDir.get().file("com/rodrigomachado/riff/core/room/RoomConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package com.rodrigomachado.riff.core.room\n\n" +
                "/** Gerado no build: o Firebase da Sala (local.properties). Vazio = Sala desligada. */\n" +
                "object RoomConfig {\n" +
                "    const val API_KEY = \"${kotlinLiteral(firebaseApiKey)}\"\n" +
                "    const val DB_URL = \"${kotlinLiteral(firebaseDbUrl)}\"\n" +
                "}\n",
        )
    }
}

kotlin {
    // A base dos testes comuns que usam o org.json (Robolectric no Android, JUnit puro no PC).
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    sourceSets {
        commonMain {
            kotlin.srcDir(generateRoomConfig)
        }
        commonMain.dependencies {
            implementation(project(":core:datastore"))
            implementation(libs.kotlinx.coroutines.core)
            // O org.json vem com o Android; no PC entra a biblioteca original.
            compileOnly(libs.org.json)
        }
        getByName("desktopMain").dependencies { implementation(libs.org.json) }
        commonTest.dependencies {
            // O Firebase falso (FakeFirebase), que o :feature:room e o :desktop também usam.
            implementation(project(":core:room:testing"))
            implementation(libs.junit4)
            implementation(libs.kotlinx.coroutines.test)
            compileOnly(libs.org.json)
        }
    }
}
