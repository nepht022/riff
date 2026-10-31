import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Módulo de telas para o celular e o PC (`riff.kmp.library`) com Compose Multiplatform: textos e fontes em
 * `composeResources`, testes de tela do Android com Roborazzi.
 */
class KmpLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("riff.kmp.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("org.jetbrains.compose")
            extensions.configure<KotlinMultiplatformExtension> {
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                    // Os recursos do Compose Multiplatform vão no APK como recursos do Android.
                    androidResources.enable = true
                }
                sourceSets.getByName("commonMain").dependencies {
                    listOf("compose-mp-runtime", "compose-mp-foundation", "compose-mp-ui", "compose-mp-material3", "compose-mp-resources")
                        .forEach { implementation(lib(it)) }
                }
                sourceSets.getByName("androidHostTest").dependencies {
                    listOf(
                        "roborazzi", "roborazzi-compose",
                        "androidx-compose-ui-test-junit4", "androidx-compose-ui-test-manifest",
                    ).forEach { implementation(lib(it)) }
                }
            }
            // Textos e fontes (composeResources) com a classe Res num pacote do módulo, ex.: ...core.ui.resources.
            extensions.getByType<ComposeExtension>().extensions.configure<ResourcesExtension> {
                packageOfResClass = "com.rodrigomachado.riff." + path.removePrefix(":").replace(':', '.') + ".resources"
            }
        }
    }
}
