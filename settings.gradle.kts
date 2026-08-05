pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "wakaroute-android"

// `core` is a plain Kotlin/JVM module, not an Android library.
//
// Every rule in the AB wiki that iOS and Android must agree on — how 理解度 is
// derived, what counts as a stumble, how the school API is read — lives there,
// and `./gradlew :core:test` runs it without an emulator. That mirrors why iOS
// keeps the same logic in the WakaRouteKit package rather than the app target.
include(":core")
include(":app")
