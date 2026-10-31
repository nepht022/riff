import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Módulo para o celular e o PC: alvo Android (plugin KMP do AGP) e alvo JVM `desktop`. Os testes do Android rodam
 * no PC (Robolectric), os do PC com JUnit 4. O namespace do Android vem do caminho do módulo
 * (`:core:ui` → `com.rodrigomachado.riff.core.ui`).
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            extensions.configure<KotlinMultiplatformExtension> {
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                    namespace = "com.rodrigomachado.riff." + path.removePrefix(":").replace(':', '.')
                    compileSdk = RiffSdk.COMPILE
                    minSdk = RiffSdk.MIN
                    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
                    withHostTest { isIncludeAndroidResources = true }
                }
                jvm("desktop") { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
                sourceSets.getByName("androidHostTest").dependencies {
                    listOf("junit4", "robolectric", "kotlinx-coroutines-test").forEach { implementation(lib(it)) }
                }
                sourceSets.getByName("desktopTest").dependencies {
                    listOf("junit4", "kotlinx-coroutines-test").forEach { implementation(lib(it)) }
                }
            }
            // O Robolectric codifica espaços do caminho do ~/.m2 como %20 e não acha os jars que baixou.
            providers.gradleProperty("riff.robolectric.mavenRepoLocal").orNull?.let { repo ->
                tasks.withType<Test>().configureEach { systemProperty("maven.repo.local", repo) }
            }
        }
    }
}
