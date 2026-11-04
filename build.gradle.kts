plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.aboutlibraries.jvm) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    // Módulos Android/JVM (main, test) e multiplataforma (commonMain, androidMain, desktopMain e os testes).
    val sourceSets = listOf("main", "test", "commonMain", "androidMain", "desktopMain", "commonTest", "androidHostTest", "desktopTest")
    source.setFrom(subprojects.flatMap { project -> sourceSets.map { project.file("src/$it/kotlin") } })
    autoCorrect = providers.gradleProperty("detektAutoCorrect").isPresent
}

// Android Lint do código de todos os módulos multiplataforma (eles não têm a variante debug do lintDebug); o lint
// do alvo do PC fica de fora (analisa o código gerado pelo Room e não é lint de Android). O CI roda este.
val lintMultiplatform = tasks.register("lintMultiplatform")
subprojects {
    val module = path
    pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
        lintMultiplatform.configure { dependsOn("$module:lintAndroidMain") }
    }
}

dependencies {
    detektPlugins(libs.detekt.ktlint.wrapper)
    kover(project(":core:chart"))
    kover(project(":core:game"))
    kover(project(":core:data"))
}

kover {
    reports {
        filters {
            excludes {
                // Código gerado e cola com o Android, verificada no aparelho e não em teste unitário.
                classes("*_Factory", "*_Factory\$*", "*_Impl", "*_Impl\$*", "*Module", "*Module_*", "Hilt_*", "*_HiltModules*")
                classes("com.rodrigomachado.riff.core.data.folder.SafFolderSource", "com.rodrigomachado.riff.core.data.folder.SafFolderAccess")
                annotatedBy("dagger.Module")
            }
        }
        verify {
            rule {
                minBound(70)
            }
        }
    }
}
