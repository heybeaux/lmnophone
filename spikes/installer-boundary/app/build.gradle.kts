// THROWAWAY SPIKE (PLA-86). Not MVP code. Build: ./gradlew -Pvc=N assembleDebug
plugins { id("com.android.application") version "8.11.0" }
android {
    namespace = "org.plainphone.spike.updater"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.plainphone.spike.updater"
        minSdk = 33; targetSdk = 35
        versionCode = (findProperty("vc") as String?)?.toInt() ?: 1
        versionName = "v$versionCode"
        // Only v1 is test-only: an app session cannot install a test-only APK (INSTALL_FAILED_TEST_ONLY).
        manifestPlaceholders["testOnly"] = (versionCode == 1).toString()
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
