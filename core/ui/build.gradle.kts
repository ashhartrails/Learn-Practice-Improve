plugins {
    alias(libs.plugins.learn.android.library)
    alias(libs.plugins.learn.android.compose)
}

android {
    namespace = "com.example.learnanything.core.ui"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.material.icons.core)
}
