// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
// THROWAWAY probe; versions match the repository toolchain.
plugins {
    id("com.android.application") version "8.11.0"
    id("org.jetbrains.kotlin.android") version "2.1.20"
}
android {
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
dependencies { testImplementation("junit:junit:4.13.2") }
