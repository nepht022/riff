import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("riff.android.library.compose")
            pluginManager.apply("riff.android.hilt")
            dependencies {
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:model"))
                add("implementation", project(":core:data"))
                add("implementation", lib("androidx-lifecycle-viewmodel-compose"))
                add("implementation", lib("androidx-lifecycle-runtime-compose"))
                add("implementation", lib("androidx-hilt-lifecycle-viewmodel-compose"))
                add("testImplementation", lib("junit4"))
                add("testImplementation", lib("kotlinx-coroutines-test"))
                add("testImplementation", lib("turbine"))
                add("testImplementation", lib("robolectric"))
                add("testImplementation", lib("roborazzi"))
                add("testImplementation", lib("roborazzi-compose"))
                add("testImplementation", lib("androidx-compose-ui-test-junit4"))
                add("debugImplementation", lib("androidx-compose-ui-test-manifest"))
            }
        }
    }
}
