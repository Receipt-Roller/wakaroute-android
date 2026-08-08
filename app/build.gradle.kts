import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Release signing, read from a file that is **not** in the repository.
 *
 * `keystore.properties` and `*.jks` are gitignored, and the reason is in that
 * file: this repository is public, and a key or password committed once cannot
 * be taken back by deleting it in a later commit. Play signs with an upload key
 * that, if leaked, has to be reset with Google before anyone can publish again.
 *
 * Absent — on CI, or on a machine that has no business publishing — the release
 * build still compiles and simply comes out unsigned. That is deliberate:
 * `assembleRelease` is run for lint and R8 far more often than for publishing,
 * and it must not require a key to do it.
 */
val signingProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

val canSignRelease = signingProperties.getProperty("storeFile") != null

android {
    namespace = "com.wakaroute.app"
    compileSdk = 36

    defaultConfig {
        // Decided in AB t-1fa7292, matching the iOS bundle id.
        applicationId = "com.wakaroute.app"

        // API 26 for the same reason iOS chose 17: students doing 高校受験 are
        // often on a hand-me-down phone. 26 reaches 2017 hardware while keeping
        // EncryptedSharedPreferences, notification channels and Compose working
        // without compatibility shims.
        minSdk = 26
        // Required by Google Play from 2026-08-31, for new apps and for every
        // update after it. See docs/play-store-submission.md.
        targetSdk = 36

        // Play requires versionCode to increase with every upload and never
        // repeat, including for a build that was rolled back. It is not the
        // version students see — versionName is.
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        // Staging is a build type rather than a runtime switch so a build
        // cannot be pointed at the wrong backend by accident — same reasoning
        // as `AppEnvironment` on iOS.
        create("staging") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            matchingFallbacks += listOf("debug")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            if (canSignRelease) signingConfig = signingConfigs.getByName("release")

            // R8 renames everything, so the stack traces in Play's pre-launch
            // report are unreadable without the mapping file it produces at
            // app/build/outputs/mapping/release/mapping.txt. **Upload it with
            // each release and keep that copy** — it only matches the build it
            // came from, and a rebuild produces a different one.
            //
            // Nothing in the app uploads a crash anywhere: §9 forbids crash
            // reporting, and that has not changed. This is for the reports Play
            // collects from its own test devices.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        warningsAsErrors = true

        // Two "a newer version exists" checks, and nothing else, are off.
        //
        // They do not describe the code: they compare it against whatever was
        // published this morning. Left on, a build that is green today fails
        // tomorrow because someone else shipped a release, which trains the
        // team to ignore lint. Dependency updates are a deliberate change with
        // its own AB task, not a build break.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion")
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // Renders the inline SVG diagrams in lesson bodies.
    //
    // Chosen over hand-drawing them onto a Canvas: these are number lines and
    // coordinate grids in a maths lesson, and a subtly wrong diagram is worse
    // than the text description we would otherwise show. AndroidSVG rather than
    // an image-loading framework because the SVG arrives as a string in the
    // lesson JSON, not as a URL to fetch.
    implementation(libs.androidsvg)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented, because the merged semantics tree — the one TalkBack
    // actually reads — only exists on a device. `uiautomator dump` renders
    // Compose hierarchies in a way that looks like every row is unlabelled,
    // including Material's own navigation bar, so it cannot be used to judge
    // this.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
