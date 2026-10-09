plugins {
    alias(libs.plugins.riff.android.application.compose)
    alias(libs.plugins.riff.android.hilt)
}

android {
    namespace = "com.rodrigomachado.riff"
    defaultConfig {
        applicationId = "com.rodrigomachado.riff"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:spotify"))
    implementation(project(":feature:library"))
    implementation(project(":feature:gameplay"))
    implementation(project(":feature:results"))
    implementation(project(":feature:nowplaying"))
    implementation(project(":feature:settings"))
    implementation(libs.androidx.navigation.compose)
}
