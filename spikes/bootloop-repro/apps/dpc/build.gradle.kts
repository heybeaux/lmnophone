// THROWAWAY SPIKE (PLA-22). testOnly DPC so `dpm remove-active-admin` works. Not MVP code.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.plainphone.spike.bootloop"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.plainphone.spike.bootloop"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "bootloop-1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
