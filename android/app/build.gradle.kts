plugins {
    alias(libs.plugins.android.application)
    // Kotlin itself comes from AGP 9 built-in Kotlin (no kotlin-android plugin).
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.example.voicecapture" // placeholder: rename once, in one commit
    // Compose 1.12+ requires compileSdk 37 (docs/pinned-versions.md); API 37 is installed on the build machine.
    compileSdk = 37
    // Pinned to the installed NDK. AGP 9.5 defaults to 28.2.13676358, which is NOT installed here.
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "org.example.voicecapture"
        minSdk = 33 // placeholder: revisit after GATE-01 (Android version of the Pixel 7)
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += "arm64-v8a" // DEC-19: the Pixel 7 is ARM64
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // STEP-11: add keep rules for the native speech library before enabling
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// No kotlin { jvmToolchain(...) } here: with built-in Kotlin the Kotlin jvmTarget defaults to
// android.compileOptions.targetCompatibility (17), which is the bytecode level we want.
// android/core still sets jvmToolchain(17) explicitly, because it uses the plain Kotlin/JVM plugin.

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
