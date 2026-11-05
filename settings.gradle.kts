pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Baixa o JDK das toolchains (o Temurin 21 que empacota o app do PC; o JBR do build não tem o jpackage).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "riff"
include(":app")
include(":core:ui")
include(":core:model")
include(":core:chart")
include(":core:game")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:room")
include(":core:room:testing")
include(":feature:library")
include(":feature:search")
include(":core:audio")
include(":core:encore")
include(":core:separation")
include(":feature:gameplay")
include(":feature:results")
include(":feature:settings")
include(":feature:calibration")
include(":feature:report")
include(":feature:ranking")
include(":feature:licenses")
include(":feature:home")
include(":desktop")
include(":baselineprofile")
