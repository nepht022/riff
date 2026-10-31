import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Tela para o celular e o PC: tela e ViewModel no código comum; no celular o Hilt cria o ViewModel por uma
 * subclasse `@HiltViewModel` em `androidMain`, no PC o app monta as dependências.
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("riff.kmp.library.compose")
            pluginManager.apply("riff.kmp.hilt")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.getByName("commonMain").dependencies {
                    implementation(project(":core:ui"))
                    implementation(project(":core:model"))
                    implementation(project(":core:data"))
                    listOf(
                        "jetbrains-lifecycle-viewmodel-compose", "jetbrains-lifecycle-runtime-compose",
                        "kotlinx-serialization-core", "kotlinx-coroutines-core",
                    ).forEach { implementation(lib(it)) }
                }
                sourceSets.getByName("androidHostTest").dependencies {
                    listOf("kotlinx-coroutines-test", "turbine").forEach { implementation(lib(it)) }
                }
            }
        }
    }
}
