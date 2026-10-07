plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.roborazzi) apply false
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
}

kover {
    reports {
        verify {
            rule {
                minBound(70)
            }
        }
    }
}
