import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Módulo de telas para o celular e o PC: alvo Android (plugin KMP do AGP) e alvo JVM `desktop`, com Compose
 * Multiplatform. Os testes do Android rodam no PC (Robolectric e Roborazzi), como nos módulos só Android.
 * O namespace do Android vem do caminho do módulo (`:core:ui` → `com.rodrigomachado.riff.core.ui`).
 */
class KmpLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("org.jetbrains.compose")
            extensions.configure<KotlinMultiplatformExtension> {
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                    namespace = "com.rodrigomachado.riff." + path.removePrefix(":").replace(':', '.')
                    compileSdk = RiffSdk.COMPILE
                    minSdk = RiffSdk.MIN
                    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
                    withHostTest { isIncludeAndroidResources = true }
                    // Os recursos do Compose Multiplatform vão no APK como recursos do Android.
                    androidResources.enable = true
                }
                jvm("desktop") { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
                sourceSets.getByName("commonMain").dependencies {
                    listOf("compose-mp-runtime", "compose-mp-foundation", "compose-mp-ui", "compose-mp-material3", "compose-mp-resources")
                        .forEach { implementation(lib(it)) }
                }
                sourceSets.getByName("androidHostTest").dependencies {
                    listOf(
                        "junit4", "robolectric", "roborazzi", "roborazzi-compose",
                        "androidx-compose-ui-test-junit4", "androidx-compose-ui-test-manifest",
                    ).forEach { implementation(lib(it)) }
                }
            }
            // Textos e fontes (composeResources) com a classe Res num pacote do módulo, ex.: ...core.ui.resources.
            extensions.getByType<ComposeExtension>().extensions.configure<ResourcesExtension> {
                packageOfResClass = "com.rodrigomachado.riff." + path.removePrefix(":").replace(':', '.') + ".resources"
            }
            // O Robolectric codifica espaços do caminho do ~/.m2 como %20 e não acha os jars que baixou.
            providers.gradleProperty("riff.robolectric.mavenRepoLocal").orNull?.let { repo ->
                tasks.withType<Test>().configureEach { systemProperty("maven.repo.local", repo) }
            }
        }
    }
}
