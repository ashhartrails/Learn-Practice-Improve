plugins {
    alias(libs.plugins.learn.android.library)
    alias(libs.plugins.learn.android.compose)
}

android {
    namespace = "com.example.learnanything.core.ui"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
