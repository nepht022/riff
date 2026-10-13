plugins {
    alias(libs.plugins.riff.android.library)
    alias(libs.plugins.riff.android.hilt)
}

android {
    namespace = "com.rodrigomachado.riff.core.separation"
    // O modelo é lido direto do APK (mapeado em memória): não pode estar comprimido.
    androidResources { noCompress += "onnx" }
    defaultConfig { consumerProguardFiles("consumer-rules.pro") }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

// O onnxruntime-android não traz bibliotecas nativas para o PC: os testes da JVM usam o de desktop.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime-android")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:audio"))
    implementation(project(":core:chart"))
    implementation(project(":core:data"))
    implementation(libs.onnxruntime.android)
    implementation(libs.jtransforms)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(libs.onnxruntime.jvm)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
}
