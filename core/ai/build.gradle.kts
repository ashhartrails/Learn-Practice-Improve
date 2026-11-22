plugins {
    alias(libs.plugins.learn.android.library)
    alias(libs.plugins.learn.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.learnanything.core.ai"
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.json.schema.validator)

    testImplementation(libs.okhttp.mockwebserver)
}
