import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

internal fun Project.addComposeDependencies() {
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    val bom = libs.findLibrary("androidx-compose-bom").get()
    dependencies {
        add("implementation", platform(bom))
        add("testImplementation", platform(bom))
        add("implementation", libs.findLibrary("androidx-compose-material3").get())
        add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
    }
}
