// THROWAWAY SPIKE (PLA-11). Not MVP code.
// Build the test-only variant (default) or, for the QR test, a non-test-only one with -PtestOnly=false.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val testOnly = (findProperty("testOnly") as String?) ?: "true"

android {
    namespace = "com.plainphone.spike.emu"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.plainphone.spike.emu"
        minSdk = 33
        targetSdk = 35
        versionCode = 2
        versionName = "emu-2-pla23"
        manifestPlaceholders["testOnly"] = testOnly
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
