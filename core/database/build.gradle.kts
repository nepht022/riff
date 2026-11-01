plugins {
    alias(libs.plugins.riff.kmp.library)
    alias(libs.plugins.riff.kmp.room)
    alias(libs.plugins.riff.kmp.hilt)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.room.testing)
        }
    }
}

// Os esquemas exportados viram assets dos testes do Android, para o teste de migração.
androidComponents {
    onVariants { variant ->
        variant.hostTests.values.forEach { it.sources.assets?.addStaticSourceDirectory("$projectDir/schemas") }
    }
}
