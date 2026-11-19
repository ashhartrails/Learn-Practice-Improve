import java.util.Properties

plugins {
    alias(libs.plugins.learn.android.library)
    alias(libs.plugins.learn.hilt)
    alias(libs.plugins.room3)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.learnanything.core.data"
}

android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        // Optional Google sign-in web client id for Drive backup (see docs/SETUP.md). Never committed.
        val props = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${props.getProperty("google.webClientId", "")}\"")
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:ai"))
    implementation(libs.room3.runtime)
    implementation(libs.androidx.sqlite.framework)
    ksp(libs.room3.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.auth)
    implementation(libs.okhttp)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)
}
