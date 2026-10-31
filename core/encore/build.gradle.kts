plugins {
    alias(libs.plugins.riff.kmp.library)
    alias(libs.plugins.riff.kmp.hilt)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:chart"))
            implementation(libs.kotlinx.coroutines.core)
            // O org.json vem com o Android; no PC entra a biblioteca original.
            compileOnly(libs.org.json)
        }
        getByName("desktopMain").dependencies { implementation(libs.org.json) }
        getByName("androidHostTest").dependencies {
            implementation(project.dependencies.testFixtures(project(":core:chart")))
        }
    }
}
