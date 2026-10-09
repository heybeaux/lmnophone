// THROWAWAY SPIKE FIXTURE (PLA-11). Not MVP code.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.plainphone.fx.urlbar"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.plainphone.fx.urlbar"
        minSdk = 33
        targetSdk = 35
        versionCode = 2
        versionName = "fx-2-pla23"
    }
    sourceSets["main"].java.srcDir("../fx-common/java")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies { implementation("androidx.browser:browser:1.8.0") }
