// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
// THROWAWAY probe; versions match the repository toolchain.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
android {
    sourceSets["main"].manifest.srcFile(if (findProperty("withoutCounts") == "true") "src/noCounts/AndroidManifest.xml" else "src/main/AndroidManifest.xml")
    namespace = "com.plainphone.spike.counts"
    compileSdk = 36
    defaultConfig {
        applicationId = (findProperty("probeId") as String?) ?: "com.plainphone.spike.counts"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "throwaway-1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { testImplementation(libs.junit4) }
