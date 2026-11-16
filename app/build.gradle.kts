plugins {
    alias(libs.plugins.learn.android.application)
    alias(libs.plugins.learn.android.compose)
    alias(libs.plugins.learn.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.learnanything"

    defaultConfig {
        applicationId = "com.example.learnanything"
        versionCode = 1
        versionName = "0.1.0"
    }

    testOptions.unitTests.isIncludeAndroidResources = true

    buildTypes {
        release {
            // Shrinking is turned on before any Play Store release; M0 is debug-only.
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:home"))
    implementation(project(":feature:create"))
    implementation(project(":feature:subject"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:topic"))
    implementation(project(":feature:test"))
    implementation(project(":feature:practical"))
    implementation(project(":feature:progress"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.kotlinx.serialization.json)

    // Robolectric smoke test: launches the real activity, Hilt graph, Room and navigation.
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room3.runtime)
    testImplementation(libs.androidx.sqlite.framework)
    debugImplementation(libs.compose.ui.test.manifest)
}
