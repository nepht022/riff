plugins {
    alias(libs.plugins.riff.android.application.compose)
    alias(libs.plugins.riff.android.hilt)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.rodrigomachado.riff"
    defaultConfig {
        applicationId = "com.rodrigomachado.riff"
        versionCode = 1
        versionName = "0.1.0"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Uso pessoal: a mesma chave do debug deixa o app medido substituir o de debug sem apagar os dados.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:audio"))
    implementation(project(":core:data"))
    implementation(project(":core:spotify"))
    implementation(project(":feature:library"))
    implementation(project(":feature:search"))
    implementation(project(":feature:gameplay"))
    implementation(project(":feature:results"))
    implementation(project(":feature:nowplaying"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:calibration"))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

// O Robolectric codifica espaços do caminho do ~/.m2 como %20 (veja o AndroidLibraryConventionPlugin).
providers.gradleProperty("riff.robolectric.mavenRepoLocal").orNull?.let { repo ->
    tasks.withType<Test>().configureEach { systemProperty("maven.repo.local", repo) }
}
