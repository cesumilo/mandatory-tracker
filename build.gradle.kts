plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

subprojects {
    afterEvaluate {
        if (plugins.hasPlugin("org.jlleitschuh.gradle.ktlint")) {
            configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
                disabledRules.set(setOf("no-wildcard-imports", "function-naming"))
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
