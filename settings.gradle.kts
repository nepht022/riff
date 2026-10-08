pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // AAR do Spotify App Remote (não está no Maven): veja third_party/README.md
        maven {
            url = uri("third_party/maven")
            content { includeGroup("com.spotify.android") }
        }
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
include(":feature:library")
include(":core:audio")
include(":core:spotify")
include(":feature:gameplay")
include(":feature:results")
