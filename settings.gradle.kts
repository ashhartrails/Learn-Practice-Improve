pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LearnAnything"

include(":app")
include(":core:model")
include(":core:data")
include(":core:ai")
include(":core:ui")
include(":feature:home")
include(":feature:create")
include(":feature:subject")
include(":feature:topic")
include(":feature:test")
include(":feature:progress")
include(":feature:settings")
