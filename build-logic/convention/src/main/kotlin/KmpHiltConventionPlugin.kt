import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Hilt num módulo multiplataforma: só o celular usa. As classes comuns mantêm `@Inject constructor` (javax.inject),
 * os módulos do Hilt ficam em `androidMain` e o processador roda só no alvo Android. O plugin Gradle do Hilt é
 * do app; numa biblioteca basta o processador.
 */
class KmpHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            extensions.configure<KotlinMultiplatformExtension> {
                // O processador do Hilt gera Java (fábricas e hilt_aggregated_deps): o alvo Android precisa compilar Java.
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") { withJava() }
                sourceSets.getByName("commonMain").dependencies { implementation(lib("javax-inject")) }
                sourceSets.getByName("androidMain").dependencies { implementation(lib("hilt-android")) }
            }
            dependencies { add("kspAndroid", lib("hilt-compiler")) }
        }
    }
}
