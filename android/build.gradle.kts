plugins {
    alias(libs.plugins.android.application) apply false
    // Kotlin for Android modules comes from AGP 9 built-in Kotlin; kotlin-android is not used.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
