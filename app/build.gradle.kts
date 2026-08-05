plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.wakaroute.app"
    compileSdk = 35

    defaultConfig {
        // Decided in AB t-1fa7292, matching the iOS bundle id.
        applicationId = "com.wakaroute.app"

        // API 26 for the same reason iOS chose 17: students doing 高校受験 are
        // often on a hand-me-down phone. 26 reaches 2017 hardware while keeping
        // EncryptedSharedPreferences, notification channels and Compose working
        // without compatibility shims.
        minSdk = 26
        targetSdk = 35

        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
