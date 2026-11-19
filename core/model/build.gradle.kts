plugins {
    alias(libs.plugins.learn.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.learnanything.core.model"
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
