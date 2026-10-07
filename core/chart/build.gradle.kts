plugins {
    alias(libs.plugins.riff.jvm.library)
    alias(libs.plugins.kover)
}

dependencies {
    api(project(":core:model"))
}
