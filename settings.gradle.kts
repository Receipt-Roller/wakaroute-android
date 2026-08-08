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

/**
 * Downloads the JDK the build asks for, instead of requiring the developer to
 * already have it.
 *
 * `core` pins a Java 17 toolchain on purpose (see its build file). Without this
 * plugin that pin means "fail unless a JDK 17 happens to be installed", which
 * turns an Android Studio upgrade into a build that cannot run at all — Studio
 * 2026.1 ships JBR 25, and a fresh machine has whatever it has.
 *
 * With it, the toolchain is a property of the build rather than of the laptop,
 * and CI needs no JDK setup step either.
 */
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
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
