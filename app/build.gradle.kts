plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.gamex.adb"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.gamex.adb"
        minSdk = 26
        targetSdk = 28
        versionCode = 1
        versionName = "0.1"
    }
}
