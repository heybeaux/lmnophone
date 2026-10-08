// THROWAWAY SPIKE (PLA-5). Not MVP code.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.plainphone.spike.dpc"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.plainphone.spike.dpc"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "spike-1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
