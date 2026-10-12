plugins {
    alias(libs.plugins.riff.jvm.library)
    alias(libs.plugins.kover)
    `java-test-fixtures`
}

dependencies {
    api(project(":core:model"))
}
