import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Room num módulo multiplataforma: banco no código comum, gerado para o celular e para o PC. O celular continua
 * com o SQLite do Android; o PC usa o SQLite embutido (`sqlite-bundled`).
 */
class KmpRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("androidx.room")
            pluginManager.apply("com.google.devtools.ksp")
            extensions.configure<RoomExtension> { schemaDirectory("$projectDir/schemas") }
            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.getByName("commonMain").dependencies { implementation(lib("androidx-room-runtime")) }
                sourceSets.getByName("desktopMain").dependencies { implementation(lib("androidx-sqlite-bundled")) }
            }
            dependencies {
                add("kspAndroid", lib("androidx-room-compiler"))
                add("kspDesktop", lib("androidx-room-compiler"))
            }
        }
    }
}
