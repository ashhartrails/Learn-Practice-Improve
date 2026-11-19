plugins {
    alias(libs.plugins.learn.android.feature)
}

android {
    namespace = "com.example.learnanything.feature.settings"
}

dependencies {
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.activity.compose)
}
