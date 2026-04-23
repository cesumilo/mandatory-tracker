plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.android.library")
}

android {
    namespace = "com.valoranttracker.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.datetime)
                api(libs.kotlinx.coroutines.core)
                api(libs.ktor.client.core)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.client.logging)
                api(libs.ktor.serialization.kotlinx.json)
            }
        }

        val androidMain by getting {
            dependencies {
                api(libs.kotlinx.coroutines.android)
                api(libs.ktor.client.okhttp)
            }
        }
    }
}