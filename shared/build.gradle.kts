plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.android.library")
}

android {
    namespace = "com.valoranttracker.shared"
    compileSdk = 34
}

kotlin {
    androidTarget()
}