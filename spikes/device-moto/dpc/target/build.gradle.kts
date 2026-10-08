// THROWAWAY SPIKE (PLA-5). A tiny app we own, used as an update source for S2.
// Build v2 with: ./gradlew :target:assembleDebug -PtargetVersion=2
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val targetVersion = (project.findProperty("targetVersion") as String?)?.toInt() ?: 1

android {
    namespace = "com.plainphone.spike.target"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.plainphone.spike.target"
        minSdk = 34
        targetSdk = 35
        versionCode = targetVersion
        versionName = "target-$targetVersion"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
