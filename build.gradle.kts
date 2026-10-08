plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    source.setFrom(subprojects.flatMap { listOf(it.file("src/main/kotlin"), it.file("src/test/kotlin")) })
    autoCorrect = providers.gradleProperty("detektAutoCorrect").isPresent
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
