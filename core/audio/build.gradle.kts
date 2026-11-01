plugins {
    alias(libs.plugins.riff.kmp.library)
    alias(libs.plugins.riff.kmp.hilt)
}

kotlin {
    (this as ExtensionAware).extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget>(
        "android",
    ) {
        // Os sons do app (res/raw) para o SoundPool.
        androidResources.enable = true
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:chart"))
            implementation(libs.kotlinx.coroutines.core)
        }
        getByName("androidHostTest").dependencies {
            implementation(project.dependencies.testFixtures(project(":core:chart")))
            implementation(libs.androidx.test.core)
        }
    }
}
