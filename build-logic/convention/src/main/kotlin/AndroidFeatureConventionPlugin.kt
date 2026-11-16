import org.gradle.api.Plugin
import org.gradle.api.Project

/** A feature module: Android library + Compose + Hilt + the shared core modules. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("learn.android.library")
        pluginManager.apply("learn.android.compose")
        pluginManager.apply("learn.hilt")
        dependencies.apply {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:data"))
            add("implementation", project(":core:ui"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("compose-material-icons-core"))
        }
    }
}
