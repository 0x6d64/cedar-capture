plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.example.voicecapture" // placeholder: rename once, in one commit
    compileSdk = 36

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

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
