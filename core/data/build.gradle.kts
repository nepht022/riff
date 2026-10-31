import java.util.Properties

plugins {
    alias(libs.plugins.riff.kmp.library)
    alias(libs.plugins.riff.kmp.hilt)
    alias(libs.plugins.kover)
}

// Endereço do script do ranking (Google Apps Script) no local.properties, fora do git. Sem ele o ranking fica
// desligado. Vira a constante RankingConfig.RANKING_URL do Android (o plugin multiplataforma não tem BuildConfig).
val rankingUrl: String = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}.getProperty("ranking.url", "")
val rankingConfigDir = layout.buildDirectory.dir("generated/rankingConfig/androidMain/kotlin")
val generateRankingConfig = tasks.register("generateRankingConfig") {
    inputs.property("rankingUrl", rankingUrl)
    outputs.dir(rankingConfigDir)
    doLast {
        val file = rankingConfigDir.get().file("com/rodrigomachado/riff/core/data/RankingConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package com.rodrigomachado.riff.core.data\n\n" +
                "internal object RankingConfig {\n    const val RANKING_URL = \"$rankingUrl\"\n}\n",
        )
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(project(":core:chart"))
            implementation(project(":core:encore"))
            implementation(project(":core:database"))
            implementation(project(":core:datastore"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.room.runtime)
            // O org.json vem com o Android; no PC entra a biblioteca original.
            compileOnly(libs.org.json)
        }
        getByName("desktopMain").dependencies { implementation(libs.org.json) }
        androidMain {
            kotlin.srcDir(generateRankingConfig)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.androidx.test.core)
            implementation(project.dependencies.testFixtures(project(":core:chart")))
        }
        getByName("desktopTest").dependencies {
            implementation(project.dependencies.testFixtures(project(":core:chart")))
        }
    }
}
