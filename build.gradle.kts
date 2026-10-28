buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Align AGP's built-in Kotlin compiler with the Compose compiler plugin.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
