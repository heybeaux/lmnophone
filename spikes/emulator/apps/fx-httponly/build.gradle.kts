// THROWAWAY SPIKE FIXTURE (PLA-11). Not MVP code.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.plainphone.fx.httponly"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.plainphone.fx.httponly"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "fx-1"
    }
    sourceSets["main"].java.srcDir("../fx-common/java")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
