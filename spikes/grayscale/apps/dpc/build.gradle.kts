// THROWAWAY SPIKE (PLA-38). Test-only DPC that toggles system greyscale. Not MVP code.
// Build a higher version for the self-update test with -Pvc=2.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val vc = ((findProperty("vc") as String?) ?: "1").toInt()

android {
    namespace = "com.plainphone.spike.grey"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.plainphone.spike.grey"
        minSdk = 33
        targetSdk = 35
        versionCode = vc
        versionName = "grey-$vc"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
