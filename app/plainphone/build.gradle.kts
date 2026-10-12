/*
 * Copyright 2026 The Plainphone Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// The app: Device Owner receiver, activities and wiring. Depends on every other module.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/** The gate's negative-control build types; each has its own `BuildControl` in `src/<name>/kotlin`. */
val NEGATIVE_CONTROLS = listOf("ctlNoPhone", "ctlNoDialerLockTask", "ctlNoAlert", "ctlNoAlertLockTask")

android {
    namespace = "org.plainphone"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "org.plainphone"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = providers.gradleProperty("previewTestRunner")
            .getOrElse("org.plainphone.counts.CombinedCountInstrumentation")
        versionCode = 30
        versionName = "0.1.0-preview-30"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jdk.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jdk.get())
    }
    buildFeatures {
        compose = true
    }
    buildTypes {
        // Release-gate negative controls (task 4.4): debug, testOnly builds that each break one
        // emergency route, so the gate can prove it fails. Never shipped.
        for (control in NEGATIVE_CONTROLS) {
            create(control) {
                initWith(getByName("debug"))
                matchingFallbacks += "debug"
            }
        }
    }
    sourceSets {
        getByName("debug").kotlin.srcDir("src/noControl/kotlin")
        getByName("release").kotlin.srcDir("src/noControl/kotlin")
        // :device's fake port, for boot-to-Home integration tests.
        getByName("test").kotlin.srcDir("../device/src/testShared/kotlin")
        for (control in NEGATIVE_CONTROLS) {
            getByName(control) {
                kotlin.srcDir("src/debug/kotlin")
                manifest.srcFile("src/debug/AndroidManifest.xml")
            }
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
}

dependencies {
    implementation(project(":core"))
    implementation(project(":policy"))
    implementation(project(":links"))
    implementation(project(":ui"))
    implementation(project(":device"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    testImplementation(libs.junit4)
}
