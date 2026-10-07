import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                compileSdk = RiffSdk.COMPILE
                defaultConfig {
                    minSdk = RiffSdk.MIN
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                testOptions {
                    unitTests.isIncludeAndroidResources = true
                }
            }
            // O Robolectric codifica espaços do caminho do ~/.m2 como %20 e não acha os jars que baixou.
            // Em máquinas com espaço no usuário, aponte para um repositório local sem espaços.
            providers.gradleProperty("riff.robolectric.mavenRepoLocal").orNull?.let { repo ->
                tasks.withType<Test>().configureEach { systemProperty("maven.repo.local", repo) }
            }
        }
    }
}
