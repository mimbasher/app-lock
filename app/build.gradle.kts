plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// CI passes -PbuildNumber=<run number>; local builds fall back to a dev version.
// versionName has to match the release tag or the in-app update check compares
// against a stale number and keeps offering the same update forever.
val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 0
val appVersionName = (project.findProperty("appVersionName") as String?) ?: "1.0-dev"

android {
    namespace = "com.turboo.applock"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.turboo.applock"
        minSdk = 26
        targetSdk = 36
        // versionCode must increase for Android to install an update in place.
        versionCode = buildNumber.coerceAtLeast(1)
        versionName = appVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Google Play forbids an app that downloads and installs its own APK, so the
    // self-updater cannot ship in a Play build. Both flavours compile against the
    // same `update` API; `play` supplies inert stubs from src/play, and the
    // INTERNET, REQUEST_INSTALL_PACKAGES and FileProvider entries live in
    // src/github/AndroidManifest.xml, so the Play build has no network access at all.
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("boolean", "SELF_UPDATE", "true")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "SELF_UPDATE", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        // For BuildConfig.SELF_UPDATE, which hides the updater UI in the Play build.
        buildConfig = true
    }

    lint {
        // Adaptive icons must live in mipmap-anydpi-v26; AAPT2 cannot resolve them from
        // a bare `anydpi` folder, so this check's suggestion does not apply here.
        disable += "ObsoleteSdkInt"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    debugImplementation(libs.androidx.ui.tooling)
}
