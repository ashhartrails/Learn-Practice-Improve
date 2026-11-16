plugins {
    alias(libs.plugins.learn.android.library)
    alias(libs.plugins.learn.hilt)
    alias(libs.plugins.room3)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.learnanything.core.data"
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

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
