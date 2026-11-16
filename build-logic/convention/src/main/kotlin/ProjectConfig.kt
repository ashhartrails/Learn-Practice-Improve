import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** Single place for SDK levels. See CLAUDE.md for why minSdk is 26. */
object Sdk {
    const val COMPILE = 37
    const val TARGET = 36
    const val MIN = 26
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

internal fun ApplicationExtension.configureCommon() {
    compileSdk = Sdk.COMPILE
    defaultConfig.minSdk = Sdk.MIN
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

internal fun LibraryExtension.configureCommon() {
    compileSdk = Sdk.COMPILE
    defaultConfig.minSdk = Sdk.MIN
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions.unitTests.isReturnDefaultValues = true
    testOptions.unitTests.isIncludeAndroidResources = true
}

internal fun Project.addUnitTestDependencies() {
    dependencies.apply {
        add("testImplementation", libs.lib("junit"))
        add("testImplementation", libs.lib("truth"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        add("testImplementation", libs.lib("turbine"))
    }
}
