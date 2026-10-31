plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.grassified.wristprompt"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.grassified.wristprompt"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "io.github.grassified.wristprompt.storage.ConversationStorageInstrumentation"
    }

    buildFeatures {
        compose = true
    }

    // One protocol implementation, compiled into both apps without another Gradle module.
    sourceSets {
        getByName("debug").kotlin.directories.add(rootProject.file("shared/src/debug/kotlin").path)
        getByName("main").kotlin.directories.add(rootProject.file("shared/src/main/kotlin").path)
        getByName("test").kotlin.directories.add(rootProject.file("shared/src/test/kotlin").path)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.commonmark)
    implementation(libs.commonmark.tables)
    implementation(libs.okhttp)
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)
    implementation(libs.play.services.wearable)
    implementation(libs.serialization.json)
    implementation(libs.datastore.preferences)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
